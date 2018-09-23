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

import com.google.common.base.Strings;

import net.talpidae.base.util.auth.AuthenticationSecurityContext;
import net.talpidae.base.util.auth.Authenticator;
import net.talpidae.base.util.session.SessionService;

import java.io.IOException;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import lombok.extern.slf4j.Slf4j;
import lombok.val;


/**
 * Request filter that replaces the SecurityContext with authentication info from a JWT.
 */
@Slf4j
@Singleton
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthBearerAuthenticationRequestFilter implements ContainerRequestFilter
{
    public static final String AUTHORIZATION_HEADER_KEY = "Authorization";

    private static final String AUTHENTICATION_SCHEME = "Bearer";

    private static final String AUTHENTICATION_SCHEME_PREFIX = AUTHENTICATION_SCHEME + " ";

    private final Authenticator authenticator;

    private final SessionService sessionService;


    @Inject
    public AuthBearerAuthenticationRequestFilter(Authenticator authenticator, SessionService sessionService)
    {
        this.authenticator = authenticator;
        this.sessionService = sessionService;
    }


    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException
    {
        if (requestContext.getSecurityContext() instanceof AuthenticationSecurityContext)
            return; // already authenticated (possibly by other filter)

        val authorization = requestContext.getHeaderString(AUTHORIZATION_HEADER_KEY);
        if (!Strings.isNullOrEmpty(authorization))
        {
            val schemeIndex = authorization.indexOf(AUTHENTICATION_SCHEME_PREFIX);
            if (schemeIndex >= 0)
            {
                val token = authorization.substring(AUTHENTICATION_SCHEME_PREFIX.length());

                val securityContext = createSecurityContext(token);
                if (securityContext != null)
                {
                    requestContext.setSecurityContext(securityContext);
                }
                else
                {
                    log.warn("token invalid: abort request for: {}", requestContext.getUriInfo().getPath());

                    // client sent a token, but it can't be trusted
                    requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
                }
            }
        }

        // no token, no authorisation, maybe ok
    }


    public AuthenticationSecurityContext createSecurityContext(String token)
    {
        val validClaims = authenticator.evaluateToken(token);
        if (validClaims != null)
        {
            return new AuthenticationSecurityContext(sessionService, validClaims.getSubject());
        }

        return null;
    }
}
