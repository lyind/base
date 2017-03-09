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

import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.config.SlaveSettings;
import net.talpidae.base.insect.exchange.message.MappingPayload;
import net.talpidae.base.insect.exchange.message.Payload;
import net.talpidae.base.insect.state.InsectState;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;


@Singleton
@Slf4j
public class SynchronousSlave extends Insect<SlaveSettings> implements Slave
{
    private static final long HEARTBEAT_WORKER_TIMEOUT = 5000;

    private static final BiFunction<String, RouteBlockHolder, RouteBlockHolder> computeRouteBlockHolder = (String route, RouteBlockHolder oldBlockHolder) ->
    {
        // we need to keep track of the original route reference for synchronisation
        return new RouteBlockHolder(oldBlockHolder != null ? oldBlockHolder.getRoute() : route);
    };

    private final Map<String, RouteBlockHolder> dependencies = new ConcurrentHashMap<>();

    private final Heartbeat heartbeat = new Heartbeat();

    @Getter
    private volatile boolean isRunning = false;


    @Inject
    public SynchronousSlave(SlaveSettings settings)
    {
        super(settings, true);
    }


    @Override
    public void run()
    {
        try
        {
            synchronized (this)
            {
                isRunning = true;
                this.notifyAll();
            }

            // spawn heartbeat thread
            final Thread heartbeatWorker = startWorker(heartbeat, "Insect-Slave-Heartbeat", HEARTBEAT_WORKER_TIMEOUT);
            try
            {
                super.run();
            }
            finally
            {
                if (joinWorker(heartbeatWorker, HEARTBEAT_WORKER_TIMEOUT))
                {
                    log.debug("successfully shutdown heartbeat worker");
                }
                else
                {
                    log.warn("shutdown of heartbeat worker timed out");
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
        RouteBlockHolder blockHolder = null;
        for (int count = 0; ; ++count)
        {
            val services = getRouteToInsects().get(route);
            if (services != null)
            {
                val selectedService = findYoungest(services);
                if (selectedService != null)
                {
                    if (blockHolder != null)
                    {
                        // remove block for route, if we still own it
                        dependencies.remove(route, blockHolder);
                    }

                    return new InetSocketAddress(selectedService.getHost(), selectedService.getPort());
                }
            }

            // indicate that we are waiting for this route to be discovered
            blockHolder = dependencies.compute(route, computeRouteBlockHolder);

            requestDependency(route);

            // wait for news on this route
            synchronized (blockHolder.getRoute())
            {
                blockHolder.getRoute().wait(850);
            }

            if (count % 3 == 0)
            {
                log.warn("findService() blocking on route: {}", route);
            }
        }
    }

    @Override
    protected void postHandleMapping(MappingPayload mapping)
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

    private void sendHeartbeat()
    {
        val heartBeatMapping = MappingPayload.builder()
                .port(getSettings().getBindAddress().getPort())
                .host(getSettings().getBindAddress().getHostString())
                .route(getSettings().getRoute())
                .build();

        addUpstreamMessage(heartBeatMapping);
    }

    private void requestDependency(String requestedRoute)
    {
        val dependencyMapping = MappingPayload.builder()
                .port(getSettings().getBindAddress().getPort())
                .host(getSettings().getBindAddress().getHostString())
                .route(getSettings().getRoute())
                .dependency(requestedRoute)
                .build();

        addUpstreamMessage(dependencyMapping);
    }

    private void addUpstreamMessage(Payload payload)
    {
        for (val remote : getSettings().getRemotes())
        {
            addMessage(remote, payload);
        }
    }

    private InsectState findYoungest(Map<InsectState, InsectState> services)
    {
        InsectState youngest = null;

        for (InsectState candidate : services.keySet())
        {
            if (youngest == null || candidate.getTimestamp() > youngest.getTimestamp())
            {
                youngest = candidate;
            }
        }

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


    private class Heartbeat implements Runnable
    {
        @Override
        public void run()
        {
            synchronized (this)
            {
                this.notifyAll();
            }

            try
            {
                do
                {
                    Thread.sleep(1000);
                    Thread.yield(); // give other threads a chance to delay us

                    // notify upstream queen about our existence and route
                    sendHeartbeat();
                }
                while (!Thread.interrupted());
            }
            catch (InterruptedException e)
            {
                log.warn("heartbeat worker interrupted");
            }
        }
    }
}
