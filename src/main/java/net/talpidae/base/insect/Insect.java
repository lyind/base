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

package net.talpidae.base.insect;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.config.InsectSettings;
import net.talpidae.base.insect.exchange.MessageExchange;
import net.talpidae.base.insect.message.InsectMessage;
import net.talpidae.base.insect.message.InsectMessageFactory;
import net.talpidae.base.insect.message.payload.Invalidate;
import net.talpidae.base.insect.message.payload.Mapping;
import net.talpidae.base.insect.message.payload.Payload;
import net.talpidae.base.insect.message.payload.Shutdown;
import net.talpidae.base.insect.state.InsectState;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public abstract class Insect<S extends InsectSettings> implements CloseableRunnable
{
    @Getter(AccessLevel.PROTECTED)
    private final MessageExchange<InsectMessage> exchange;

    private final boolean onlyTrustedRemotes;

    // route -> Set<InsectState> (we use a map to efficiently lookup insects)
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, Map<InetSocketAddress, InsectState>> routeToInsects = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PROTECTED)
    private final S settings;

    @Getter
    private volatile boolean isRunning = false;

    private Thread executingThread;


    Insect(S settings, boolean onlyTrustedRemotes)
    {
        this.settings = settings;
        this.onlyTrustedRemotes = onlyTrustedRemotes;
        this.exchange = new MessageExchange<>(new InsectMessageFactory(), settings);
    }

    private static Map<InetSocketAddress, InsectState> newInsectStates(String route)
    {
        return new ConcurrentHashMap<>();
    }

    @Override
    public void run()
    {
        executingThread = Thread.currentThread();
        try
        {
            synchronized (this)
            {
                isRunning = true;
                notifyAll();
            }

            routeToInsects.clear();

            // spawn exchange thread
            val exchangeWorker = InsectWorker.start(exchange, exchange.getClass().getSimpleName());
            try
            {
                log.info("MessageExchange running on {}", settings.getBindAddress().toString());
                while (!Thread.interrupted())
                {
                    final InsectMessage message = exchange.take();
                    try
                    {
                        handleMessage(message);
                    }
                    catch (Throwable e)
                    {
                        log.error("error handling message from remote {}: {}: {}", message.getRemoteAddress(), e.getClass().getSimpleName(), e.getMessage());
                    }
                    finally
                    {
                        exchange.recycle(message);
                    }
                }
            }
            catch (InterruptedException e)
            {
                log.warn("message loop interrupted, shutting down");
            }
            finally
            {
                // shutdown message exchange
                exchange.close();

                // cause worker thread to finish executing
                if (exchangeWorker.shutdown())
                {
                    log.info("MessageExchange was shut down");
                }
            }
        }
        finally
        {
            isRunning = false;
        }
    }

    /**
     * Override if additional actions are required before shutting down.
     */
    protected void prepareShutdown()
    {
    }

    @Override
    public void close()
    {
        prepareShutdown();

        exchange.close();
        if (executingThread != null)
        {
            executingThread.interrupt();
        }
    }

    /**
     * Override to implement additional logic after a mapping message has been handled by the default handler.
     */
    protected void postHandleMapping(Mapping mapping, boolean isNewMapping)
    {

    }

    /**
     * Override to implement remote shutdown.
     */
    protected void handleShutdown()
    {

    }

    /**
     * Override to implement remote shutdown.
     */
    protected void handleInvalidate()
    {

    }

    /**
     * Pack and send a message with the specified payload to the given destination.
     */
    protected void addMessage(InetSocketAddress destination, Payload payload)
    {
        val exchange = getExchange();
        try
        {
            val message = exchange.allocate();

            message.setPayload(payload, destination);

            exchange.add(message);
        }
        catch (NoSuchElementException e)
        {
            log.error("failed to propagate mapping to {}, reason: {}", destination.toString(), e.getMessage());
        }
    }

    private void handleMessage(InsectMessage message)
    {
        val remote = message.getRemoteAddress();
        val isTrustedServer = settings.getRemotes().contains(remote);
        if (isTrustedServer || !onlyTrustedRemotes)
        {
            try
            {
                val payload = message.getPayload(!isTrustedServer);
                if (payload instanceof Mapping)
                {
                    // validate client address (avoid message spoofing)
                    if (isTrustedServer || ((Mapping) payload).isAuthorative(remote))
                    {
                        handleMapping((Mapping) payload);
                    }
                    else
                    {
                        log.warn("possible spoofing: remote {} not authorized to send message: {}", remote, payload);
                    }
                }
                else if (payload instanceof Invalidate)
                {
                    log.debug("received invalidate message from remote {}", remote);
                    handleInvalidate();
                }
                else if (payload instanceof Shutdown)
                {
                    log.debug("received shutdown message from remote {}", remote);
                    handleShutdown();
                }
            }
            catch (IndexOutOfBoundsException e)
            {
                log.warn("received malformed message from: {}", remote);
            }
        }
        else
        {
            log.warn("rejected message from untrusted source: {}", remote);
        }
    }

    private void handleMapping(Mapping mapping)
    {
        val alternatives = routeToInsects.computeIfAbsent(mapping.getRoute(), Insect::newInsectStates);
        val key = mapping.getSocketAddress();

        val nextStateBuilder = InsectState.builder()
                .timestamp(mapping.getTimestamp());

        // do we have an existing entry for this slave?
        final boolean isNewMapping;
        val state = alternatives.get(key);
        if (state != null)
        {
            isNewMapping = false;

            // merge new dependency with those already known
            nextStateBuilder.dependencies(state.getDependencies());
            val newDependency = mapping.getDependency();
            if (!newDependency.isEmpty())
            {
                nextStateBuilder.dependency(newDependency);
            }

            // do not keep resolving the same host:port combo
            nextStateBuilder.socketAddress(state.getSocketAddress());
        }
        else
        {
            isNewMapping = true;

            // resolve once
            nextStateBuilder.socketAddress(new InetSocketAddress(mapping.getHost(), mapping.getPort()));
        }

        // InsectState is key and value at the same time
        alternatives.put(key, nextStateBuilder.build());

        // let descendants add more actions
        postHandleMapping(mapping, isNewMapping);
    }
}

