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
import com.google.common.eventbus.EventBus;
import com.google.inject.Singleton;

import net.talpidae.base.event.Invalidate;
import net.talpidae.base.event.Shutdown;
import net.talpidae.base.insect.config.SlaveSettings;
import net.talpidae.base.insect.message.payload.Mapping;
import net.talpidae.base.insect.message.payload.Metrics;
import net.talpidae.base.insect.message.payload.Payload;
import net.talpidae.base.insect.state.InsectState;
import net.talpidae.base.insect.state.ServiceState;
import net.talpidae.base.util.network.NetworkUtil;
import net.talpidae.base.util.performance.Metric;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Singleton
@Slf4j
public class SyncSlave extends Insect<SlaveSettings> implements Slave
{
    private static final long DEPENDENCY_RESEND_MILLIES_MIN = TimeUnit.MILLISECONDS.toMillis(100);

    private static final long DEPENDENCY_RESEND_MILLIES_MAX = TimeUnit.SECONDS.toMillis(12);

    private final Map<String, RouteWaiter> dependencies = new ConcurrentHashMap<>();

    private final EventBus eventBus;

    private final NetworkUtil networkUtil;

    private long nextHeartBeatNanos = 0L;

    @Getter
    private volatile boolean isRunning = false;

    @Inject
    public SyncSlave(SlaveSettings settings, EventBus eventBus, NetworkUtil networkUtil)
    {
        super(settings, true);

        this.eventBus = eventBus;
        this.networkUtil = networkUtil;
    }


    @Override
    public void run()
    {
        try
        {
            synchronized (this)
            {
                isRunning = true;
                notifyAll();
            }

            if (Strings.isNullOrEmpty(getSettings().getRoute()))
            {
                log.debug("argument for parameter \"route\" is empty, won't publish anything");
            }

            nextHeartBeatNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(getSettings().getPulseDelay());
            super.run();
        }
        finally
        {
            isRunning = false;
        }
    }

    /**
     * Try to find a service for route, register route as a dependency and block in case it isn't available immediately.
     */
    @Override
    public InetSocketAddress findService(String route) throws InterruptedException
    {
        return findService(route, Long.MAX_VALUE);
    }

    /**
     * Try to find a service for route, register route as a dependency and block in case it isn't available immediately.
     *
     * @return Address of discovered service if one was discovered before a timeout occurred, null otherwise.
     */
    @Override
    public InetSocketAddress findService(String route, long timeoutMillies) throws InterruptedException
    {
        // we may occasionally get an empty collection from findServices()
        val alternatives = findServices(route, timeoutMillies);
        if (!alternatives.isEmpty())
        {
            // pick a random service from the pool
            return alternatives.get(random.nextInt(alternatives.size())).getSocketAddress();
        }

        // timeout
        return null;
    }

    /**
     * Return all known services for route, register route as a dependency and block in case there are none available immediately.
     *
     * @return Discovered services if any were discovered before a timeout occurred, empty list otherwise.
     */
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public List<? extends ServiceState> findServices(String route, long timeoutMillies) throws InterruptedException
    {
        List<? extends ServiceState> alternatives = lookupServices(route);
        if (!alternatives.isEmpty())
        {
            // fast path
            return alternatives;
        }

        long now = System.nanoTime();
        val timeout = (timeoutMillies >= 0) ? TimeUnit.NANOSECONDS.toMillis(now) + timeoutMillies : Long.MAX_VALUE;
        long waitInterval = DEPENDENCY_RESEND_MILLIES_MIN;

        val routeWaiter = dependencies.computeIfAbsent(route, k -> new RouteWaiter());
        do
        {
            // indicate that we are waiting for this route to be discovered
            switch (routeWaiter.advanceDiscoveryState(now))
            {
                case SEND:
                    // send out discovery request
                    requestDependency(route);

                    // fall-through

                case DONE:
                    alternatives = lookupServices(route);
                    if (!alternatives.isEmpty())
                    {
                        routeWaiter.setDiscoveryComplete();
                        dependencies.remove(route);

                        return alternatives;
                    }
            }

            // wait for news on this route
            val maxRemainingMillies = timeout - TimeUnit.NANOSECONDS.toMillis(now);
            val waitMillies = Math.min(Math.min(waitInterval, maxRemainingMillies), DEPENDENCY_RESEND_MILLIES_MAX);
            if (waitMillies >= 0L)
            {
                synchronized (routeWaiter)
                {
                    routeWaiter.wait(waitMillies);
                }
            }
            else
            {
                break;
            }

            waitInterval = waitInterval * 2;
            now = System.nanoTime();
        }
        while (true);

        log.warn("findService(): timeout for route: {}", route);
        return alternatives;
    }


    @Override
    public void forwardMetrics(Queue<Metric> metricQueue)
    {
        val metrics = Metrics.builder()
                .metrics(metricQueue)
                .build();

        sendToRemotes((settings, remote) -> metrics);
    }


    private List<? extends ServiceState> lookupServices(String route)
    {
        return getRouteToInsects().getOrDefault(route, EMPTY_ROUTE).getActive();
    }


    @Override
    protected void postHandleMapping(InsectState state, Mapping mapping, boolean isNewMapping, boolean isDependencyMapping)
    {
        if (isNewMapping)
        {
            // notify findService() callers blocking for route discovery
            val routeWaiter = dependencies.get(mapping.getRoute());
            if (routeWaiter != null)
            {
                routeWaiter.setDiscoveryComplete();
                dependencies.remove(mapping.getRoute());
            }
        }
    }

    @Override
    protected void handleShutdown()
    {
        // tell listeners that we received a shutdown request
        eventBus.post(new Shutdown());
    }

    @Override
    protected void handleInvalidate()
    {
        // drop cached remotes
        getRouteToInsects().values().forEach(InsectCollection::clear);

        // tell listeners that we received an invalidate request
        eventBus.post(new Invalidate());
    }


    @Override
    protected long handlePulse()
    {
        val now = System.nanoTime();
        if (now >= nextHeartBeatNanos)
        {
            sendHeartbeat();

            // scheduled next heartbeat, taking overshoot (delay) of this heartbeat into account
            nextHeartBeatNanos += getPulseDelayNanos() - Math.max(1L, (now - nextHeartBeatNanos));
        }

        return Math.max(1L, nextHeartBeatNanos - now);
    }


    private void sendHeartbeat()
    {
        val bindSocketAddress = getSettings().getBindAddress();
        val hostAddress = getSettings().getBindAddress().getAddress();
        val port = getSettings().getBindAddress().getPort();

        sendToRemotes((settings, remote) ->
        {
            val remoteAddress = remote.getAddress();

            final String host = (hostAddress != null)
                    ? networkUtil.getReachableLocalAddress(hostAddress, remoteAddress).getHostAddress()
                    : bindSocketAddress.getHostString();

            return Mapping.builder()
                    .host(host)
                    .port(port)
                    .route(settings.getRoute())
                    .name(settings.getName())
                    .socketAddress(InetSocketAddress.createUnresolved(host, port))
                    .build();
        });
    }


    private void requestDependency(String requestedRoute)
    {
        val bindSocketAddress = getSettings().getBindAddress();
        val hostAddress = getSettings().getBindAddress().getAddress();
        val port = getSettings().getBindAddress().getPort();

        sendToRemotes((settings, remote) ->
        {
            val remoteAddress = remote.getAddress();

            final String host = (hostAddress != null)
                    ? networkUtil.getReachableLocalAddress(hostAddress, remoteAddress).getHostAddress()
                    : bindSocketAddress.getHostString();

            return Mapping.builder()
                    .host(host)
                    .port(port)
                    .route(settings.getRoute())
                    .name(settings.getName())
                    .dependency(requestedRoute)
                    .socketAddress(InetSocketAddress.createUnresolved(host, port))
                    .build();
        });
    }


    private void sendToRemotes(BiFunction<SlaveSettings, InetSocketAddress, Payload> payloadProducer)
    {
        val settings = getSettings();
        for (val remote : settings.getRemotes())
        {
            addMessage(remote, payloadProducer.apply(settings, remote));
        }
    }


    private static final class RouteWaiter
    {
        private final AtomicLong discoveryState = new AtomicLong(0L);

        private volatile long resendNanos = TimeUnit.MILLISECONDS.toNanos(DEPENDENCY_RESEND_MILLIES_MIN);


        RouteWaiter()
        {
        }


        State advanceDiscoveryState(long currentNanos)
        {
            val state = discoveryState.get();
            if (state <= currentNanos - resendNanos)
            {
                if (discoveryState.compareAndSet(state, currentNanos))
                {
                    resendNanos = Math.min(resendNanos * 2, DEPENDENCY_RESEND_MILLIES_MAX);
                    return State.SEND;
                }
            }
            else if (state == Long.MAX_VALUE)
            {
                // discovery complete
                return State.DONE;
            }

            return State.WAIT;
        }


        State getDiscoveryState()
        {
            return discoveryState.get() == Long.MAX_VALUE ? State.DONE : State.WAIT;
        }


        void setDiscoveryComplete()
        {
            discoveryState.set(Long.MAX_VALUE);

            synchronized (this)
            {
                notifyAll();
            }
        }


        enum State
        {
            WAIT,
            DONE,
            SEND
        }
    }
}