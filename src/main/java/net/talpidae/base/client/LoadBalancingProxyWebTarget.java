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

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.val;


@Singleton
public class LoadBalancingProxyWebTarget<T>
{
    private final T resource;

    
    @Inject
    public LoadBalancingProxyWebTarget(Class<T> serviceInterfaceClass, LoadBalancingWebTargetFactory webTargetFactory)
    {
        // by convention we always use the fully qualified interface name as route
        val route = serviceInterfaceClass.getName();

        this.resource = webTargetFactory.newWebTarget(route).proxy(serviceInterfaceClass);
    }


    /**
     * Get a new or existing client for the service interface. Make sure go through this method
     * if load balancing is necessary.
     */
    public T getProxyWebTarget()
    {
        return resource;
    }
}
