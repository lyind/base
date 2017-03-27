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

import lombok.val;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.proxy.WebResourceFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.ClientBuilder;


@Singleton
public class LoadBalancingClientFactory<T>
{
    private final T resource;


    @Inject
    public LoadBalancingClientFactory(Class<T> serviceInterfaceClass, ClientConfig clientConfig)
    {
        // by convention we always use the fully qualified interface name as route
        val route = serviceInterfaceClass.getName();

        val client = ClientBuilder.newClient(clientConfig);

        // host/port are replaced later by LoadBalancingRequestFilter
        val target = client.target("http://127.0.0.1:0");
        target.property(LoadBalancingRequestFilter.ROUTE_PROPERTY_NAME, route);

        this.resource = WebResourceFactory.newResource(serviceInterfaceClass, target);
    }


    /** Get a new or existing client for the service interface. Make sure go through this method
     * if load balancing is necessary.
     */
    public T getResource()
    {
        return resource;
    }
}
