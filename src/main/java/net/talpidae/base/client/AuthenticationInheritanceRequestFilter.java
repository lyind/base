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

import com.google.inject.ConfigurationException;
import com.google.inject.Injector;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;

import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static net.talpidae.base.resource.AuthBearerAuthenticationRequestFilter.AUTHORIZATION_HEADER_KEY;
import static net.talpidae.base.resource.AuthenticationRequestFilter.SESSION_TOKEN_FIELD_NAME;


/**
 * Client request filter that tries to get "Authorization Bearer" or "X-Session-Token" header
 * from the ContainerRequestContext and copies it to this request.
 */
@Singleton
@Provider
@Slf4j
public class AuthenticationInheritanceRequestFilter implements ClientRequestFilter
{
    private final Injector injector;

    @Inject
    public AuthenticationInheritanceRequestFilter(Injector injector)
    {
        this.injector = injector;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException
    {
        try
        {
            if (Strings.isNullOrEmpty(requestContext.getHeaderString(AUTHORIZATION_HEADER_KEY))
                    && Strings.isNullOrEmpty(requestContext.getHeaderString(SESSION_TOKEN_FIELD_NAME)))
            {
                val headers = injector.getInstance(HttpHeaders.class);
                if (headers != null)
                {
                    val authBearerToken = headers.getHeaderString(AUTHORIZATION_HEADER_KEY);
                    if (!Strings.isNullOrEmpty(authBearerToken))
                    {
                        requestContext.getHeaders().putSingle(AUTHORIZATION_HEADER_KEY, authBearerToken);
                    }
                    else
                    {
                        val token = headers.getHeaderString(SESSION_TOKEN_FIELD_NAME);
                        if (!Strings.isNullOrEmpty(token))
                        {
                            requestContext.getHeaders().putSingle(SESSION_TOKEN_FIELD_NAME, token);
                        }
                    }
                }
            }
        }
        catch (IllegalStateException | ConfigurationException e)
        {
            // we are optional. if we have no request scope at this point, we just don't attach the token
        }
    }
}
