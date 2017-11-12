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

import com.google.inject.Inject;

import net.talpidae.base.util.auth.AuthenticationSecurityContext;
import net.talpidae.base.util.auth.Credentials;
import net.talpidae.base.util.session.Session;
import net.talpidae.base.util.session.SessionService;

import org.glassfish.jersey.internal.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static com.google.common.base.Strings.isNullOrEmpty;


@Slf4j
@Provider
@Priority(Priorities.AUTHENTICATION)
public class BasicAuthAuthenticationFilter implements ContainerRequestFilter
{
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private static final String AUTHORIZATION_HEADER_KEY = "Authorization";

    private static final String AUTHENTICATION_SCHEME = "Basic";

    private static final String AUTHENTICATION_SCHEME_PREFIX = AUTHENTICATION_SCHEME + " ";

    private final CredentialValidator credentialValidator;

    private final SessionService sessionService;


    @Inject
    public BasicAuthAuthenticationFilter(CredentialValidator credentialValidator, SessionService sessionService)
    {
        this.credentialValidator = credentialValidator;
        this.sessionService = sessionService;
    }


    @Override
    public void filter(ContainerRequestContext requestContext)
    {
        if (requestContext.getSecurityContext() instanceof AuthenticationSecurityContext)
            return; // already authenticated (possibly by other filter)

        val authorization = requestContext.getHeaderString(AUTHORIZATION_HEADER_KEY);
        if (!isNullOrEmpty(authorization))
        {
            val schemeIndex = authorization.indexOf(AUTHENTICATION_SCHEME_PREFIX);
            if (schemeIndex >= 0)
            {
                val encodedPassword = authorization.substring(AUTHENTICATION_SCHEME_PREFIX.length());

                // we allow UTF-8 username/password encoding
                val usernameAndPassword = new String(Base64.decode(encodedPassword.getBytes(StandardCharsets.US_ASCII)), StandardCharsets.UTF_8);

                val separatorIndex = usernameAndPassword.indexOf(':');

                val builder = Credentials.builder();
                if (separatorIndex < 0)
                {
                    // got no separating colon, only username?
                    builder.name(usernameAndPassword);
                    builder.password("");
                }
                else
                {
                    builder.name(usernameAndPassword.substring(0, separatorIndex));
                    builder.password(usernameAndPassword.substring(separatorIndex + 1, usernameAndPassword.length()));
                }

                val credentials = builder.build();
                val loginId = credentialValidator.validate(credentials);
                if (loginId != null)
                {
                    val session = sessionService.get(NIL_UUID.toString());
                    if (session != null)
                    {
                        session.getAttributes().put(Session.ATTRIBUTE_PRINCIPAL, loginId.toString());

                        val securityContext = new AuthenticationSecurityContext(sessionService, session.getId());
                        requestContext.setSecurityContext(securityContext);

                        return;
                    }
                    else
                    {
                        log.error("failed to create session for user: {}", credentials.getName());
                    }
                }

                log.warn("BasicAuth username or password invalid: abort request for: {}", requestContext.getUriInfo().getPath());

                // client sent credentials, but they aren't valid
                requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            }
        }
    }
}