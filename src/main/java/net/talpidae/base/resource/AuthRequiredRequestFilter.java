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

package net.talpidae.base.resource;

import net.talpidae.base.util.auth.AuthRequired;
import net.talpidae.base.util.auth.AuthenticationSecurityContext;

import java.io.IOException;

import javax.annotation.Priority;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import lombok.extern.slf4j.Slf4j;
import lombok.val;


/**
 * Name-bound filter that checks for authentication info to be present and the session to be present, rejects request otherwise.
 */
@Slf4j
@Singleton
@Provider
@AuthRequired
@Priority(Priorities.AUTHORIZATION)
public class AuthRequiredRequestFilter implements ContainerRequestFilter
{
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException
    {
        val securityContext = requestContext.getSecurityContext();
        if (securityContext instanceof AuthenticationSecurityContext)
        {
            // check if the session still exists (won't after a logout)
            if (((AuthenticationSecurityContext) securityContext).getSession() != null)
            {
                // pass
                return;
            }
        }

        // no valid security context present, abort request
        requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
}
