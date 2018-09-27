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

import net.talpidae.base.util.session.SessionService;

import javax.ws.rs.core.SecurityContext;

import lombok.val;

import static net.talpidae.base.util.session.Session.ATTRIBUTE_ROLES;


public class AuthenticationSecurityContext implements SecurityContext
{
    private final SessionPrincipal sessionPrincipal;


    public AuthenticationSecurityContext(SessionService sessionService, String sessionId)
    {
        this.sessionPrincipal = new SessionPrincipal(sessionService, sessionId);
    }


    @Override
    public SessionPrincipal getUserPrincipal()
    {
        return sessionPrincipal;
    }


    @Override
    public boolean isUserInRole(String role)
    {
        val roles = sessionPrincipal.getSession().getAttributes().get(ATTRIBUTE_ROLES);
        for (val r : roles.split(","))
        {
            if (r.equalsIgnoreCase(role))
                return true;
        }

        return false;
    }

    @Override
    public boolean isSecure()
    {
        return true;
    }

    @Override
    public String getAuthenticationScheme()
    {
        return FORM_AUTH;
    }
}
