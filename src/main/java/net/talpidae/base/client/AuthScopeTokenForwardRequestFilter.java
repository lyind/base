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

import com.google.inject.Provider;

import net.talpidae.base.resource.AuthenticationRequestFilter;
import net.talpidae.base.util.auth.scope.AuthenticationTokenHolder;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


/**
 * Client request filter that tries to get X-Session-Token header from the ContainerRequestContext and copies it to this request.
 */
@Singleton
@javax.ws.rs.ext.Provider
@Slf4j
public class AuthScopeTokenForwardRequestFilter implements ClientRequestFilter
{
    private final Provider<AuthenticationTokenHolder> authenticationTokenHolderProvider;

    @Inject
    public AuthScopeTokenForwardRequestFilter(Provider<AuthenticationTokenHolder> authenticationTokenHolderProvider)
    {
        this.authenticationTokenHolderProvider = authenticationTokenHolderProvider;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException
    {
        val tokenHolder = authenticationTokenHolderProvider.get();
        if (tokenHolder != null)
        {
            val token = tokenHolder.getToken();
            if (token != null)
            {
                if (!Strings.isNullOrEmpty(token))
                {
                    requestContext.getHeaders().putSingle(AuthenticationRequestFilter.AUTHORIZATION_HEADER_KEY, "Bearer " + token);
                }
            }
        }
    }
}