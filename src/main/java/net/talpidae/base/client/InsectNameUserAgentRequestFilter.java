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

import net.talpidae.base.insect.config.SlaveSettings;

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static com.google.common.base.Strings.isNullOrEmpty;


/**
 * Adds the insect name to the user-agent header value.
 */
@Singleton
@Provider
@Slf4j
public class InsectNameUserAgentRequestFilter implements ClientRequestFilter
{
    private final String insectName;


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Inject
    public InsectNameUserAgentRequestFilter(Optional<SlaveSettings> slaveSettings)
    {
        insectName = slaveSettings.map(SlaveSettings::getName).orElse(null);
    }


    @Override
    public void filter(ClientRequestContext requestContext) throws IOException
    {
        if (insectName != null)
        {
            val userAgent = requestContext.getHeaderString(HttpHeaders.USER_AGENT);
            val nextUserAgent = isNullOrEmpty(userAgent) ? insectName : insectName + "/" + userAgent;

            requestContext.getHeaders().putSingle(HttpHeaders.USER_AGENT, nextUserAgent);
        }
    }
}
