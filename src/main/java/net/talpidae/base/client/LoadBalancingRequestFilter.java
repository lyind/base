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

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.Insect;
import net.talpidae.base.insect.Slave;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;


@Singleton
@Provider
@Slf4j
public class LoadBalancingRequestFilter implements ClientRequestFilter
{
    // Must be a valid port that indicates route lookup
    public static final String ROUTE_PROPERTY_NAME = Insect.class.getPackage().toString() + ".Route";

    private final Slave slave;

    @Inject
    public LoadBalancingRequestFilter(Slave slave)
    {
        this.slave = slave;
    }


    private static String getRequestRoute(ClientRequestContext requestContext)
    {
        val routeObject = requestContext.getConfiguration().getProperty(ROUTE_PROPERTY_NAME);
        if (routeObject instanceof String)
        {
            return (String) routeObject;
        }

        return null;
    }


    private URI replaceHostAndPort(URI target, String host, int port)
    {
        try
        {
            return new URI(target.getScheme(),
                    target.getUserInfo(), host, port,
                    target.getPath(), target.getQuery(), target.getFragment());
        }
        catch (URISyntaxException e)
        {
            log.error("failed to rewrite request for {}:{}", host, port);
        }

        return null;
    }


    @Override
    public void filter(ClientRequestContext requestContext) throws IOException
    {
        // if we have no route property attached there is no action to perform
        val route = getRequestRoute(requestContext);
        if (route != null)
        {
            try
            {
                val address = slave.findService(route);
                if (address != null)
                {
                    // successfully redirected request
                    val rewrittenUri = replaceHostAndPort(requestContext.getUri(), address.getHostString(), address.getPort());
                    if (rewrittenUri != null)
                    {
                        requestContext.setUri(rewrittenUri);
                    }

                    return;
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            log.debug("failed to lookup route for ");
            requestContext.abortWith(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Unable to lookup service for route: " + route)
                    .build());
        }
    }
}
