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

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.exchange.InsectMessage;
import net.talpidae.base.insect.exchange.InsectMessageFactory;
import net.talpidae.base.insect.exchange.MessageExchange;
import net.talpidae.base.insect.exchange.message.MappingPayload;
import net.talpidae.base.insect.exchange.message.Payload;
import net.talpidae.base.insect.state.InsectState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Thread.State.TERMINATED;


@Singleton
@Slf4j
public class SynchronousQueen implements Queen
{
    private static final long EXCHANGE_SHUTDOWN_TIMEOUT = 1986;

    private final MessageExchange<InsectMessage> exchange;

    // route -> Set<InsectState> (we use a map to efficiently lookup insects)
    private final Map<String, Map<InsectState, InsectState>> routeToInsects = new ConcurrentHashMap<>();

    private final QueenSettings settings;

    private volatile boolean keepRunning;


    @Inject
    public SynchronousQueen(QueenSettings settings)
    {
        this.settings = settings;
        this.exchange = new MessageExchange<>(new InsectMessageFactory(), settings.getBindHost());
    }


    private static Map<InsectState, InsectState> newInsectStates(String route)
    {
        return new ConcurrentHashMap<>();
    }


    @Override
    public void run()
    {
        keepRunning = true;
        routeToInsects.clear();

        // spawn exchange thread
        final Thread exchangeWorker = startWorker();
        try
        {
            log.info("MessageExchange running on {}:{}", settings.getBindHost().toString(), exchange.getPort());
            while (keepRunning)
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
            keepRunning = false;
            log.warn("message loop interrupted, shutting down");
        }
        finally
        {
            // cause worker thread to finish executing
            if (!joinWorker(exchangeWorker))
            {
                log.warn("MessageExchange didn't shutdown in time");
            }
            else
            {
                log.info("MessageExchange was shut down");
            }
        }
    }


    public void shutdown()
    {
        keepRunning = false;
        exchange.shutdown();
    }


    private void handleMessage(InsectMessage message)
    {
        val remote = message.getRemoteAddress();
        val isTrustedServer = settings.getAuthorizedRemotes().contains(remote.getAddress());
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


    private void handleMapping(MappingPayload mapping)
    {
        val alternatives = routeToInsects.computeIfAbsent(mapping.getRoute(), SynchronousQueen::newInsectStates);
        val nextState = InsectState.builder()
                .timestamp(mapping.getTimestamp())
                .host(mapping.getHost())
                .port(mapping.getPort())
                .path(mapping.getPath())
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

        // tell others about it
        relayMapping(mapping);
    }


    /**
     * Relay updates to all interested services (those that have this services route in their dependencies).
     */
    private void relayMapping(final MappingPayload mapping)
    {
        if (!Strings.isNullOrEmpty(mapping.getDependency()))
        {
            // do not relay dependency map update packages (mostly done on service startup anyways)
            return;
        }

        routeToInsects.forEach((route, states) ->
        {
            if (route != null && !route.equals(mapping.getRoute()))
            {
                states.forEach((stateKey, stateValue) ->
                {
                    try
                    {
                        val message = exchange.allocate();

                        message.setPayload(mapping, new InetSocketAddress(stateValue.getHost(), stateValue.getPort()));
                        exchange.add(message);
                    }
                    catch (NoSuchElementException e)
                    {
                        log.error("failed to propagate mapping to {}:{}, reason: {}", stateValue.getHost(), stateValue.getPort(), e.getMessage());
                    }
                });
            }
        });
    }


    private Thread startWorker()
    {
        val exchangeWorker = new Thread(exchange);
        exchangeWorker.setName("InsectQueen-MessageExchange");
        exchangeWorker.start();

        return exchangeWorker;
    }


    private boolean joinWorker(Thread exchangeWorker)
    {
        // cause worker thread to finish executing
        exchange.shutdown();
        try
        {
            exchangeWorker.join(EXCHANGE_SHUTDOWN_TIMEOUT);
        }
        catch (InterruptedException e)
        {
            exchangeWorker.interrupt();
        }

        return exchangeWorker.getState() == TERMINATED;
    }
}
