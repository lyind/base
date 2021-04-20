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

package net.talpidae.base.client;

import com.google.inject.Provider;

import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.val;


@Singleton
public class LoadBalancingProxyWebTarget<T>
{
    private final AtomicReference<T> resourceRef = new AtomicReference<>(null);

    private final Provider<LoadBalancingWebTargetFactory> webTargetFactoryProvider;

    private final Class<T> serviceInterface;


    @Inject
    public LoadBalancingProxyWebTarget(Class<T> serviceInterfaceClass, Provider<LoadBalancingWebTargetFactory> webTargetFactoryProvider)
    {
        this.serviceInterface = serviceInterfaceClass;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
    }


    /**
     * Get a new or existing client for the service interface. Make sure go through this method
     * if load balancing is necessary.
     */
    public T getProxyWebTarget()
    {
        val resource = resourceRef.get();
        if (resource == null)
        {
            // by convention we always use the fully qualified interface name as route
            val newResource = webTargetFactoryProvider.get().newWebTarget(serviceInterface.getSimpleName()).proxy(serviceInterface);
            if (resourceRef.compareAndSet(null, newResource))
            {
                return newResource;
            }

            return resourceRef.get();
        }

        return resource;
    }
}
