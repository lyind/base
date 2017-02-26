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
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.util.auth.AuthenticationSecurityContext;
import net.talpidae.base.util.auth.Authenticator;
import net.talpidae.base.util.session.SessionService;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;


/**
 * Request filter that replaces the SecurityContext with authentication info from a JWT.
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationRequestFilter implements ContainerRequestFilter
{
    public static final String SESSION_TOKEN_FIELD_NAME = "X-Session-Token";

    private final Authenticator authenticator;

    private final SessionService sessionService;

    private String[] keys;


    @Inject
    public AuthenticationRequestFilter(Authenticator authenticator, SessionService sessionService)
    {
        this.authenticator = authenticator;
        this.sessionService = sessionService;
    }


    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException
    {
        val token = requestContext.getHeaderString(SESSION_TOKEN_FIELD_NAME);
        if (!Strings.isNullOrEmpty(token))
        {
            val keys = getKeys();
            val parser = Jwts.parser();
            for (int i = 0; i < keys.length; ++i)
            {
                try
                {
                    val claims = parser.setSigningKey(keys[i]).parseClaimsJws(token).getBody();
                    val sessionId = claims.getSubject();

                    requestContext.setSecurityContext(new AuthenticationSecurityContext(sessionService, sessionId));

                    return;
                }
                catch (JwtException e)
                {
                    log.debug("key {}: invalid session token: {}", i, e.getMessage());
                }
            }

            log.warn("token invalid: request aborted for: {}", requestContext.getUriInfo().getPath());

            // client sent a token, but it can't be trusted
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        // no token, no authorisation, maybe ok
    }


    /**
     * Get keys for validating JWT.
     */
    private String[] getKeys()
    {
        if (keys == null)
        {
            keys = authenticator.getKeys();
        }

        return keys;
    }
}
