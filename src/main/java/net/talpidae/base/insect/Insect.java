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
import net.talpidae.base.insect.exchange.InsectMessage;
import net.talpidae.base.insect.exchange.InsectMessageFactory;
import net.talpidae.base.insect.exchange.MessageExchange;
import net.talpidae.base.insect.exchange.message.MappingPayload;
import net.talpidae.base.insect.exchange.message.Payload;
import net.talpidae.base.insect.state.InsectState;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Thread.State.TERMINATED;


@Slf4j
public abstract class Insect<S extends InsectSettings> implements CloseableRunnable
{
    private static final long MESSAGE_EXCHANGE_WORKER_TIMEOUT = 6000;

    @Getter(AccessLevel.PROTECTED)
    private final MessageExchange<InsectMessage> exchange;

    private final boolean onlyTrustedRemotes;

    // route -> Set<InsectState> (we use a map to efficiently lookup insects)
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, Map<InsectState, InsectState>> routeToInsects = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PROTECTED)
    private final S settings;

    @Getter
    private volatile boolean isRunning = false;

    private Thread executingThread;


    protected Insect(S settings, boolean onlyTrustedRemotes)
    {
        this.settings = settings;
        this.onlyTrustedRemotes = onlyTrustedRemotes;
        this.exchange = new MessageExchange<>(new InsectMessageFactory(), settings);
    }

    private static Map<InsectState, InsectState> newInsectStates(String route)
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
                this.notifyAll();
            }

            routeToInsects.clear();

            // spawn exchange thread
            final Thread exchangeWorker = startWorker(exchange, "Insect-MessageExchange", MESSAGE_EXCHANGE_WORKER_TIMEOUT);
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
                    catch (Exception e)
                    {
                        log.error("exception handling message from remote", e);
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
                if (!joinWorker(exchangeWorker, MESSAGE_EXCHANGE_WORKER_TIMEOUT))
                {
                    log.warn("MessageExchange didn't shutdown in time");
                }
                else
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


    private void handleMessage(InsectMessage message)
    {
        val remote = message.getRemoteAddress();
        val isTrustedServer = settings.getRemotes().contains(remote);
        if (isTrustedServer || !onlyTrustedRemotes)
        {
            try
            {
                Payload payload = message.getPayload(!isTrustedServer);

                if (payload instanceof MappingPayload)
                {
                    handleMapping((MappingPayload) payload);
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


    protected void handleMapping(MappingPayload mapping)
    {
        val alternatives = routeToInsects.computeIfAbsent(mapping.getRoute(), Insect::newInsectStates);
        val nextState = InsectState.builder()
                .timestamp(mapping.getTimestamp())
                .host(mapping.getHost())
                .port(mapping.getPort())
                .dependency(mapping.getDependency())
                .build();

        // if we knew already about this service, merge in known dependencies
        val state = alternatives.get(nextState);
        if (state != null)
        {
            nextState.getDependencies().addAll(state.getDependencies());
        }

        // InsectState is key and value at the same time
        alternatives.put(nextState, nextState);

        // let descendants add more actions
        postHandleMapping(mapping);
    }


    /**
     * Override to implement additional logic after a mapping message has been handled by the default handler.
     */
    protected void postHandleMapping(MappingPayload mapping)
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


    protected Thread startWorker(Runnable task, String name, long workerTimeout)
    {
        val worker = new Thread(task);
        worker.setName(name);

        final long before = System.currentTimeMillis();
        worker.start();
        try
        {
            synchronized (task)
            {
                task.wait(workerTimeout);
                log.debug("worker {} startup took {}ms", name, System.currentTimeMillis() - before);
            }
        }
        catch (InterruptedException e)
        {
            log.warn("wait for startup of {} was interrupted", name);
        }

        return worker;
    }


    protected boolean joinWorker(Thread worker, long timeout)
    {
        try
        {
            worker.interrupt();
            worker.join(timeout);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        return worker.getState() == TERMINATED;
    }
}

