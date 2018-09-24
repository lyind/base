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


import net.talpidae.base.server.ServerConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
@Singleton
@Provider
public class DefaultGenericExceptionMapper implements ExceptionMapper<Throwable>
{
    private static final int DEFAULT_STATUS_CODE = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

    private final boolean doLog;

    @Inject
    public DefaultGenericExceptionMapper(ServerConfig serverConfig)
    {
        doLog = serverConfig.isLoggingFeatureEnabled();
    }

    @Override
    public Response toResponse(Throwable e)
    {
        final int status = (e instanceof WebApplicationException)
                ? ((WebApplicationException) e).getResponse().getStatus()
                : DEFAULT_STATUS_CODE;

        if (doLog)
        {
            log.error("error: status: " + status, e);
        }

        val message = e.getMessage();
        val saneMessage = message == null || message.startsWith("RESTEASY")
                ? toStatusText(status)
                : e.getMessage();
        return Response.status(status)
                .entity(new GenericError(status, saneMessage))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }


    private static String toStatusText(int status)
    {
        val statusType = Response.Status.fromStatusCode(status);
        return statusType != null
                ? statusType.getReasonPhrase()
                : Integer.toString(status);
    }
}