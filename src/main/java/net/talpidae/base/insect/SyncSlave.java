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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.event.Invalidate;
import net.talpidae.base.event.Shutdown;
import net.talpidae.base.insect.config.SlaveSettings;
import net.talpidae.base.insect.message.payload.Mapping;
import net.talpidae.base.insect.state.ServiceState;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


@Singleton
@Slf4j
public class SyncSlave extends Insect<SlaveSettings> implements Slave
{
    private static final long DEPENDENCY_RESEND_MILLIES_MIN = TimeUnit.MILLISECONDS.toMillis(100);

    private static final long DEPENDENCY_RESEND_MILLIES_MAX = TimeUnit.SECONDS.toMillis(12);

    private final Map<String, RouteBlockHolder> dependencies = new ConcurrentHashMap<>();

    private final Heartbeat heartBeat = new Heartbeat(this);

    private final EventBus eventBus;

    @Getter
    private volatile boolean isRunning = false;

    @Inject
    public SyncSlave(SlaveSettings settings, EventBus eventBus)
    {
        super(settings, true);

        this.eventBus = eventBus;
    }


    private static RouteBlockHolder computeRouteBlockHolder(String route, RouteBlockHolder oldBlockHolder)
    {
        // we need to keep track of the original route reference for synchronisation
        return new RouteBlockHolder(oldBlockHolder != null ? oldBlockHolder.getRoute() : route);
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

            // spawn heartBeat thread
            final InsectWorker heartbeatWorker = InsectWorker.start(heartBeat, heartBeat.getClass().getSimpleName());
            try
            {
                super.run();
            }
            finally
            {
                if (heartbeatWorker.shutdown())
                {
                    log.debug("successfully shutdown heartBeat worker");
                }
            }
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
        val serviceIterator = findServices(route, timeoutMillies);
        if (serviceIterator != null)
        {
            return findYoungest(serviceIterator).getSocketAddress();
        }

        return null;
    }


    /**
     * Return all known services for route, register route as a dependency and block in case there are none available immediately.
     *
     * @return Discovered services if any were discovered before a timeout occurred, null otherwise.
     */
    @Override
    public Iterator<? extends ServiceState> findServices(String route, long timeoutMillies) throws InterruptedException
    {
        val timeout = (timeoutMillies >= 0) ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + timeoutMillies : Long.MAX_VALUE;
        long waitInterval = DEPENDENCY_RESEND_MILLIES_MIN;

        RouteBlockHolder blockHolder = null;
        do
        {
            Iterator<? extends ServiceState> serviceIterator = lookupServices(route, blockHolder);
            if (serviceIterator != null)
            {
                return serviceIterator;
            }

            // send out discovery request
            requestDependency(route);

            // indicate that we are waiting for this route to be discovered
            blockHolder = dependencies.compute(route, SyncSlave::computeRouteBlockHolder);

            synchronized (blockHolder.getRoute())
            {
                // try to lookup service again, something may have happened in between
                // (discovery response/update for same service)
                serviceIterator = lookupServices(route, blockHolder);
                if (serviceIterator != null)
                {
                    return serviceIterator;
                }

                // wait for news on this route
                val maxRemainingMillies = timeout - TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                val waitMillies = Math.min(Math.min(waitInterval, maxRemainingMillies), DEPENDENCY_RESEND_MILLIES_MAX);
                waitInterval = waitInterval * 2;

                if (waitMillies >= 0L)
                {
                    blockHolder.getRoute().wait(waitMillies);
                }
                else
                {
                    log.warn("findService(): timeout for route: {}", route);
                    return null;
                }
            }
        }
        while (true);
    }


    private Iterator<? extends ServiceState> lookupServices(String route, RouteBlockHolder blockHolder)
    {
        val services = getRouteToInsects().get(route);
        if (services != null)
        {
            val servicesIterator = services.values().iterator();
            if (servicesIterator.hasNext())
            {
                // remove block for route, if we still own it
                dependencies.remove(route, blockHolder);

                return servicesIterator;
            }
        }

        return null;
    }


    @Override
    protected void postHandleMapping(Mapping mapping, boolean isNewMapping)
    {
        if (isNewMapping)
        {
            // notify findService() callers blocking for route discovery
            val blockHolder = dependencies.get(mapping.getRoute());
            if (blockHolder != null)
            {
                synchronized (blockHolder.getRoute())
                {
                    blockHolder.getRoute().notifyAll();
                }
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
        getRouteToInsects().values().forEach(Map::clear);

        // tell listeners that we received an invalidate request
        eventBus.post(new Invalidate());
    }

    private void requestDependency(String requestedRoute)
    {
        val settings = getSettings();
        val host = settings.getBindAddress().getHostString();
        val port = settings.getBindAddress().getPort();

        val dependencyMapping = Mapping.builder()
                .host(host)
                .port(port)
                .route(settings.getRoute())
                .name(settings.getName())
                .dependency(requestedRoute)
                .socketAddress(InetSocketAddress.createUnresolved(host, port))
                .build();

        for (val remote : settings.getRemotes())
        {
            addMessage(remote, dependencyMapping);
        }
    }

    private ServiceState findYoungest(Iterator<? extends ServiceState> services)
    {
        ServiceState youngest = null;
        do
        {
            val candidate = services.next();
            if (youngest == null || candidate.getTimestamp() > youngest.getTimestamp())
            {
                youngest = candidate;
            }
        }
        while (services.hasNext());

        return youngest;
    }


    @Getter
    private static final class RouteBlockHolder
    {
        private final String route;


        RouteBlockHolder(String route)
        {
            this.route = route;
        }
    }
}
