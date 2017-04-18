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

package net.talpidae.base.util.auth;

import lombok.val;

import javax.ws.rs.container.ContainerRequestContext;


public final class AuthenticationSecurityContextExtractor
{
    /**
     * Extract a possible AuthenticationSecurityContext out of an available ContainerRequestContext.
     */
    public static AuthenticationSecurityContext extract(ContainerRequestContext context)
    {
        val securityContext = context.getSecurityContext();
        if (securityContext instanceof AuthenticationSecurityContext)
        {
            return (AuthenticationSecurityContext) securityContext;
        }

        return null;
    }


    private AuthenticationSecurityContextExtractor()
    {

    }
}
