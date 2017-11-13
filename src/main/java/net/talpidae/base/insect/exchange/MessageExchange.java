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
import net.talpidae.base.util.pool.SoftReferenceObjectPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static com.google.common.base.Strings.nullToEmpty;


@Slf4j
public abstract class MessageExchange<M extends BaseMessage> implements CloseableRunnable
{
    private static final int MESSAGE_POOL_HARD_LIMIT = 512;

    private final InsectSettings settings;

    private final SoftReferenceObjectPool<M> messagePool;

    private final Queue<M> inbound = new ArrayDeque<>();

    private final Queue<M> outbound = new ArrayDeque<>();

    private final ExchangeMessageQueueControl queueControl = new ExchangeMessageQueueControl();

    private final AtomicLong runThreadId = new AtomicLong(-1);

    private Selector selector;

    private int activeInterestOps = SelectionKey.OP_READ;

    @Getter
    private int port = 0;

    private String lastErrorMessage;


    public MessageExchange(Supplier<M> messageFactory, InsectSettings settings)
    {
        this.messagePool = new SoftReferenceObjectPool<>(messageFactory, MESSAGE_POOL_HARD_LIMIT);
        this.settings = settings;
    }


    /**
     * Wakeup exchange to force sending outbound messages and/or processing.
     */
    protected void wakeup() throws IllegalStateException
    {
        // make sure our raw is processed as soon as possible
        if (selector != null)
        {
            selector.wakeup();
        }
    }

    /**
     * Override this to process incoming messages and react to periodic events.
     * <p>
     * This allows to handle almost all logic on the same thread as the exchange and thus avoid
     * extensive locking for thread-safety.
     *
     * @return Returns the maximum delay im milliseconds until the next invocation of this method.
     */
    protected abstract long processMessages(MessageQueueControl<M> control);

    public void run()
    {
        try
        {
            if (!runThreadId.compareAndSet(-1L, Thread.currentThread().getId()))
            {
                log.debug("already running, won't enter run again");
                return;
            }

            synchronized (this)
            {
                notifyAll();
            }

            val key = setup();
            val channel = (DatagramChannel) key.channel();

            log.info("MessageExchange running on {}", settings.getBindAddress().toString());

            long maxWaitMillies;
            M outboundMessage = null;
            while (!Thread.interrupted())
            {
                try
                {
                    maxWaitMillies = processMessages(queueControl);

                    // recycle messages removed from the inbound queue by processMessages()
                    queueControl.recycleConsumedMessages();

                    if (outboundMessage == null)
                    {
                        // look for new messages
                        outboundMessage = pollAndUpdateInterestSet(key);
                    }

                    selector.select(maxWaitMillies);
                    if (key.isValid())
                    {
                        boolean mayReadMore = key.isReadable();
                        boolean mayWriteMore = key.isWritable() && outboundMessage != null;

                        while (mayReadMore || mayWriteMore)
                        {
                            mayReadMore = mayReadMore && tryReceive(channel);

                            mayWriteMore = mayWriteMore
                                    && trySend(channel, outboundMessage)
                                    && ((outboundMessage = outbound.poll()) != null);
                        }
                    }
                }
                catch (IOException e)
                {
                    handleSelectError(e);
                }
            }
        }
        catch (Exception e)
        {
            handleRunError(e);
        }
        finally
        {
            close();
            runThreadId.set(-1);
        }
    }


    public boolean isRunning()
    {
        return runThreadId.get() != -1L;
    }


    /**
     * Get access to the current queue control.
     *
     * @return Queue control object if currently executing processMessages() and inside the same thread, null otherwise.
     */
    protected MessageQueueControl getQueueControl()
    {
        val threadId = Thread.currentThread().getId();
        if (threadId == runThreadId.get())
        {
            return queueControl;
        }

        return null;
    }

    @Override
    public void close()
    {
        if (selector != null)
        {
            try { selector.close(); } catch (IOException e) { /* ignore */ }
        }
    }

    /**
     * Rate limit by exception message.
     *
     * @return The exceptions message as obtained from getMessage() if not rate-limited, null otherwise.
     */
    private String rateLimitByMessage(IOException e)
    {
        val message = nullToEmpty(e.getMessage());
        if (lastErrorMessage == null || !lastErrorMessage.equals(message))
        {
            lastErrorMessage = message;
            return message;
        }

        // rate-limited
        return null;
    }

    /**
     * Handle error in main run() method.
     */
    private void handleRunError(Exception e)
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


    /**
     * Handle error during select().
     */
    private void handleSelectError(IOException e)
    {
        val message = rateLimitByMessage(e);
        if (message != null)
        {
            log.error("error selecting keys: {}", message);
        }
    }

    /**
     * Handle error during receive().
     */
    private void handleReceiveError(IOException e)
    {
        val message = rateLimitByMessage(e);
        if (message != null)
        {
            log.error("error during receive: {}", message);
        }
    }

    /**
     * Log a send error and information about the dropped message.
     */
    private void handleSendError(IOException e, M outboundMessage)
    {
        val message = rateLimitByMessage(e);
        if (message != null)
        {
            if (message.equals("Invalid argument"))
            {
                log.error("error during send: {}, socket bound to {}", message, settings.getBindAddress());
            }
            else
            {
                log.error("error during send: {}", message);
            }
        }

        // last error was same, drop this message
        log.error("dropping outbound {} to {}",
                outboundMessage.getClass().getSimpleName(),
                outboundMessage.getRemoteAddress());
    }


    private SelectionKey setup() throws IOException
    {
        outbound.clear();
        inbound.clear();
        lastErrorMessage = null;
        selector = Selector.open();

        val channel = DatagramChannel.open();

        val bindAddress = settings.getBindAddress();
        channel.socket().bind(bindAddress);
        port = channel.socket().getLocalPort();

        channel.configureBlocking(false);

        return channel.register(selector, activeInterestOps);
    }


    private boolean tryReceive(DatagramChannel channel)
    {
        val message = messagePool.borrow();
        try
        {
            if (message.receiveFrom(channel))
            {
                inbound.add(message);
                return true;
            }
        }
        catch (IOException e)
        {
            handleReceiveError(e);
        }

        // nothing received, return message to pool
        recycleMessage(message);
        return false;
    }


    private boolean trySend(DatagramChannel channel, M message)
    {
        try
        {
            if (!message.sendTo(channel))
            {
                // not ready for writing, try again later
                return false;
            }
        }
        catch (IOException e)
        {
            handleSendError(e, message);
        }

        // message handled or dropped because of send error
        recycleMessage(message);
        return true;
    }


    private M pollAndUpdateInterestSet(SelectionKey key)
    {
        final M message = outbound.poll();

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


    private void recycleMessage(M message)
    {
        message.passivate();
        messagePool.recycle(message);
    }


    private class ExchangeMessageQueueControl implements MessageQueueControl<M>
    {
        private final List<M> consumedInboundMessages = new ArrayList<>();


        @Override
        public M addOutbound(InetSocketAddress remoteAddress)
        {
            val message = messagePool.borrow();

            message.setRemoteAddress(remoteAddress);
            outbound.add(message);

            // caller may fill message now
            return message;
        }

        @Override
        public M pollInbound()
        {
            val message = inbound.poll();
            if (message != null)
            {
                consumedInboundMessages.add(message);
            }

            return message;
        }

        void recycleConsumedMessages()
        {
            for (int i = consumedInboundMessages.size() - 1; i >= 0; --i)
            {
                recycleMessage(consumedInboundMessages.remove(i));
            }
        }
    }
}