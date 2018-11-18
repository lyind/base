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

import net.talpidae.base.util.session.Session;
import net.talpidae.base.util.session.SessionService;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import lombok.Getter;
import lombok.val;

import static net.talpidae.base.util.session.Session.ATTRIBUTE_PRINCIPAL;


public class SessionPrincipal implements Principal
{
    private final SessionService sessionService;

    private final String sessionId;

    private AtomicReference<Session> sessionRef = new AtomicReference<>(null);


    public SessionPrincipal(SessionService sessionService, String sessionId)
    {
        this.sessionService = sessionService;
        this.sessionId = sessionId;
    }


    public UUID getSessionId()
    {
        return UUID.fromString(sessionId);
    }


    @Override
    public String getName()
    {
        return getSession().getAttributes().get(ATTRIBUTE_PRINCIPAL);
    }


    /**
     * Update authentication information via AuthenticationClient.
     */
    public Session getSession()
    {
        val instance = sessionRef.get();
        if (instance == null)
        {
            val newInstance = sessionService.get(sessionId);
            if (sessionRef.compareAndSet(null, newInstance))
            {
                return newInstance;
            }

            return sessionRef.get();
        }

        return instance;
    }
}
