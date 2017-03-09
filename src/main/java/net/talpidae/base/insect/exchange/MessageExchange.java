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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.CloseableRunnable;
import net.talpidae.base.insect.config.InsectSettings;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.SoftReferenceObjectPool;

import java.io.IOException;
import java.nio.channels.*;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;


@Slf4j
public class MessageExchange<M extends BaseMessage> implements CloseableRunnable
{
    private final SoftReferenceObjectPool<M> messagePool;

    private final ArrayDeque<M> inbound = new ArrayDeque<>();

    private final ArrayDeque<M> outbound = new ArrayDeque<>();

    private final byte[] LOCK = new byte[0];

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


    public M take() throws InterruptedException
    {
        M message;
        do
        {
            message = poll();
            if (message != null)
            {
                return message;
            }
            else
            {
                assertSelectorActive();

                // wait for new inbound messages
                synchronized (inbound)
                {
                    inbound.wait(250);
                }
            }
        }
        while (true);
    }


    public void add(M message) throws IllegalStateException
    {
        assertSelectorActive();

        synchronized (outbound)
        {
            outbound.add(message);
        }

        // make sure our raw is processed as soon as possible
        selector.wakeup();
    }


    public M allocate()
    {
        try
        {
            return messagePool.borrowObject();
        }
        catch (Exception e)
        {
            throw new NoSuchElementException("failed to allocated message: " + e.getMessage());
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
            log.warn("exception recycling raw object: {}", e.getMessage());
        }
    }


    public void run()
    {
        try
        {
            selector = Selector.open();

            synchronized (this)
            {
                isRunning = true;
                // notify callers that we are initialized/started
                this.notifyAll();
            }

            val channel = DatagramChannel.open();

            channel.socket().bind(settings.getBindAddress());
            port = channel.socket().getLocalPort();

            channel.configureBlocking(false);

            val key = channel.register(selector, activeInterestOps);

            M message = null;
            do
            {
                try
                {
                    if (message == null)
                    {
                        // look for new messages
                        message = pollAndUpdateInterestSet(key);
                    }

                    selector.select(1000);
                    try
                    {
                        if (key.isValid())
                        {
                            // we push through messages until it doesn't work anymore
                            boolean receive = key.isReadable();
                            boolean send = key.isWritable();
                            do
                            {
                                if (receive)
                                {
                                    receive = tryReceive(channel);
                                }

                                // only try sending when there is a raw available
                                send = send && message != null;
                                if (send)
                                {
                                    send = trySend(channel, message);
                                    if (send)
                                    {
                                        // fetch next outbound raw
                                        message = pollOutbound();
                                    }
                                }
                            }
                            while (receive || send);
                        }
                    }
                    catch (IOException e)
                    {
                        log.error("error during receive/send: {}", e.getMessage(), e);
                    }
                }
                catch (IOException e)
                {
                    log.error("error selecting keys: {}", e.getMessage(), e);
                }
            }
            while (!Thread.interrupted());
        }
        catch (Exception e)
        {
            if (e instanceof ClosedSelectorException
                    || e instanceof ClosedChannelException
                    || e instanceof CancelledKeyException
                    || e instanceof InterruptedException)
            {
                log.info("shutting down, reason: {}", e.getMessage());
            }
            else
            {
                log.error("error setting up server socket: {}", e.getMessage(), e);
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
        synchronized (LOCK)
        {
            if (selector != null)
            {
                try { selector.close(); } catch (IOException e) { /* ignore */ }
                selector = null;
            }
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


    private void assertSelectorActive()
    {
        if (selector == null)
        {
            throw new IllegalStateException("selector is closed, call run()");
        }
    }
}