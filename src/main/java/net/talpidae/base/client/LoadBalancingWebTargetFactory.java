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

import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import lombok.NonNull;
import lombok.val;


@Singleton
public class LoadBalancingWebTargetFactory
{
    private final Client client;

    @Inject
    public LoadBalancingWebTargetFactory(@NonNull ClientConfiguration clientConfig)
    {
        client = ClientBuilder.newClient(clientConfig);
    }


    /**
     * Get a new or existing client for the specified route (service interface name). Make sure go through this method
     * if load balancing is necessary.
     */
    public ResteasyWebTarget newWebTarget(@NonNull String route)
    {
        // host/port are replaced later by LoadBalancingRequestFilter
        val webTarget = client.target("http://" + route);

        return (ResteasyWebTarget) webTarget;
    }
}
