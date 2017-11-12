/*
 * Copyright (C) 2017  Jonas Zeiger <jonas.zeiger@talpidae.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.talpidae.base.insect.exchange;

import net.talpidae.base.insect.CloseableRunnable;
import net.talpidae.base.insect.config.InsectSettings;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.SoftReferenceObjectPool;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static com.google.common.base.Strings.nullToEmpty;


@Slf4j
public class MessageExchange<M extends BaseMessage> implements CloseableRunnable
{
    private final SoftReferenceObjectPool<M> messagePool;

    private final ArrayDeque<M> inbound = new ArrayDeque<>();

    private final ArrayDeque<M> outbound = new ArrayDeque<>();

    private final InsectSettings settings;

    @Getter
    private volatile boolean isRunning;

    private Selector selector;

    private int activeInterestOps = SelectionKey.OP_READ;

    @Getter
    private int port = 0;


    public MessageExchange(PooledObjectFactory<M> messageFactory, InsectSettings settings)
    {
        this.settings = settings;
        this.messagePool = new SoftReferenceObjectPool<>(messageFactory);
    }


    /**
     * Wait for the next message to arrive up to the specified number of milliseconds.
     *
     * @return Next message if one has been received, null if the wait timed out.
     */
    public M take(long timeoutNanos) throws InterruptedException
    {
        val message = poll();
        if (message != null)
            return message;

        val millies = TimeUnit.NANOSECONDS.toMillis(timeoutNanos);
        val nanos = (int) (timeoutNanos - TimeUnit.MILLISECONDS.toNanos(millies));
        // wait for new inbound messages
        synchronized (inbound)
        {
            inbound.wait(millies, nanos);
        }

        return poll();
    }


    public void add(M message) throws IllegalStateException
    {
        synchronized (outbound)
        {
            outbound.add(message);
        }

        // make sure our raw is processed as soon as possible
        if (selector != null)
        {
            selector.wakeup();
        }
    }


    public M allocate()
    {
        try
        {
            return messagePool.borrowObject();
        }
        catch (Exception e)
        {
            throw new NoSuchElementException("failed to allocate message: " + e.getMessage());
        }
    }


    public void recycle(M message)
    {
        try
        {
            messagePool.returnObject(message);
        }
        catch (Exception e)
        {
            log.warn("failed to recycle message: {}", e.getMessage());
        }
    }


    public void run()
    {
        try
        {
            synchronized (this)
            {
                isRunning = true;
                notifyAll();
            }

            selector = Selector.open();

            val channel = DatagramChannel.open();

            channel.socket().bind(settings.getBindAddress());
            port = channel.socket().getLocalPort();

            channel.configureBlocking(false);

            val key = channel.register(selector, activeInterestOps);

            String lastErrorMessage = null;
            M outboundMessage = null;
            while (!Thread.interrupted())
            {
                try
                {
                    if (outboundMessage == null)
                    {
                        // look for new messages
                        outboundMessage = pollAndUpdateInterestSet(key);
                    }

                    selector.select(1000);
                    if (key.isValid())
                    {
                        boolean mayReadMore = key.isReadable();
                        boolean mayWriteMore = key.isWritable() && outboundMessage != null;

                        while (mayReadMore || mayWriteMore)
                        {
                            try
                            {
                                mayReadMore = mayReadMore && tryReceive(channel);
                            }
                            catch (IOException e)
                            {
                                val message = nullToEmpty(e.getMessage());
                                if (lastErrorMessage == null || !lastErrorMessage.equals(message))
                                {
                                    lastErrorMessage = message;
                                    log.error("error during receive: {}", message);
                                }
                            }

                            try
                            {
                                mayWriteMore = mayWriteMore
                                        && trySend(channel, outboundMessage)
                                        && ((outboundMessage = pollOutbound()) != null);
                            }
                            catch (IOException e)
                            {
                                val message = nullToEmpty(e.getMessage());
                                if (lastErrorMessage == null || !lastErrorMessage.equals(message))
                                {
                                    lastErrorMessage = message;

                                    if (message.equals("Invalid argument"))
                                    {
                                        log.error("error during send: {}, socket bound to {}", message, settings.getBindAddress());
                                    }
                                    else
                                    {
                                        log.error("error during send: {}", message);
                                    }
                                }
                                else if (outboundMessage != null)
                                {
                                    // drop message
                                    log.error("dropping outbound {} to {}",
                                            outboundMessage.getClass().getSimpleName(),
                                            outboundMessage.getRemoteAddress());

                                    outboundMessage = null;
                                    mayWriteMore = false;
                                }
                            }
                        }

                        lastErrorMessage = null;
                    }
                }
                catch (IOException e)
                {
                    val message = nullToEmpty(e.getMessage());
                    if (lastErrorMessage == null || !lastErrorMessage.equals(message))
                    {
                        lastErrorMessage = message;
                        log.error("error selecting keys: {}", message);
                    }
                }
            }
        }
        catch (Exception e)
        {
            if (e instanceof ClosedSelectorException
                    || e instanceof ClosedChannelException
                    || e instanceof CancelledKeyException
                    || e instanceof InterruptedException)
            {
                log.info("shutting down, reason: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            }
            else
            {
                log.error("error during socket operation", e);
            }
        }
        finally
        {
            close();
            isRunning = false;
        }
    }


    @Override
    public void close()
    {
        if (selector != null)
        {
            try { selector.close(); } catch (IOException e) { /* ignore */ }
        }

        synchronized (inbound)
        {
            inbound.clear();
            inbound.notifyAll();
        }

        synchronized (outbound)
        {
            outbound.clear();
        }

        messagePool.clear();
    }


    private M poll()
    {
        synchronized (inbound)
        {
            return inbound.poll();
        }
    }


    private boolean tryReceive(DatagramChannel channel) throws Exception
    {
        val message = messagePool.borrowObject();

        if (message.receiveFrom(channel))
        {
            synchronized (inbound)
            {
                inbound.add(message);
                inbound.notify();
            }
            return true;
        }
        else
        {
            // didn't receive anything, return raw to pool
            messagePool.returnObject(message);
            return false;
        }
    }


    private boolean trySend(DatagramChannel channel, M message) throws Exception
    {
        if (message.sendTo(channel))
        {
            messagePool.returnObject(message);
            return true;
        }
        else
        {
            return false;
        }
    }


    private M pollOutbound()
    {
        synchronized (outbound)
        {
            return outbound.poll();
        }
    }


    private M pollAndUpdateInterestSet(SelectionKey key)
    {
        final M message = pollOutbound();

        final int interestOps;
        if (message != null)
        {
            // we have packets to write and will still tryReceive
            interestOps = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
        }
        else
        {
            // nothing to trySend, still interested inbound receiving
            interestOps = SelectionKey.OP_READ;
        }

        if (interestOps != activeInterestOps)
        {
            activeInterestOps = interestOps;
            key.interestOps(interestOps);
        }

        return message;
    }
}
