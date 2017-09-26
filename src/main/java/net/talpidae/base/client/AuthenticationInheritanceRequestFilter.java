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

import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.resource.AuthenticationRequestFilter;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.ServiceLocator;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.ext.Provider;
import java.io.IOException;


/**
 * Client request filter that tries to get X-Session-Token header from the ContainerRequestContext and copies it to this request.
 */
@Singleton
@Provider
@Slf4j
public class AuthenticationInheritanceRequestFilter implements ClientRequestFilter
{
    private final ServiceLocator serviceLocator;

    @Inject
    public AuthenticationInheritanceRequestFilter(ServiceLocator serviceLocator)
    {
        this.serviceLocator = serviceLocator;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException
    {
        try
        {
            if (Strings.isNullOrEmpty(requestContext.getHeaderString(AuthenticationRequestFilter.SESSION_TOKEN_FIELD_NAME)))
            {
                val containerRequestContext = serviceLocator.getService(ContainerRequestContext.class);
                if (containerRequestContext != null)
                {
                    val token = containerRequestContext.getHeaders().getFirst(AuthenticationRequestFilter.SESSION_TOKEN_FIELD_NAME);
                    if (!Strings.isNullOrEmpty(token))
                    {
                        requestContext.getHeaders().putSingle(AuthenticationRequestFilter.SESSION_TOKEN_FIELD_NAME, token);
                    }
                }
            }
        }
        catch (MultiException e)
        {
            if (!(e.getCause() instanceof IllegalStateException))
            {
                throw e;
            }
        }
        catch (IllegalStateException e)
        {
            // we are optional. if we have no request scope at this point, we just don't attach the token
        }
    }
}
