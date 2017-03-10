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

package net.talpidae.base.insect.config;

import com.google.common.base.Strings;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.server.ServerConfig;
import net.talpidae.base.util.BaseArguments;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


@Singleton
@Setter
@Getter
@Slf4j
public class DefaultSlaveSettings implements SlaveSettings
{
    @NonNull
    private InetSocketAddress bindAddress;

    @NonNull
    private Set<InetSocketAddress> remotes;

    @NonNull
    private String route;

    private long pulseDelay = DEFAULT_PULSE_DELAY;

    private long restInPeaceTimeout;


    @Inject
    public DefaultSlaveSettings(ServerConfig serverConfig, BaseArguments baseArguments)
    {
        this.bindAddress = new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort());

        val parser = baseArguments.getOptionParser();
        val routeOption = parser.accepts("insect.slave.route").withRequiredArg().required();
        val remoteOption = parser.accepts("insect.slave.remote").withRequiredArg().required();
        val timeoutOption = parser.accepts("insect.slave.timeout").withRequiredArg().ofType(Long.class).defaultsTo(DEFAULT_REST_IN_PEACE_TIMEOUT);
        val options = baseArguments.parse();

        this.route = options.valueOf(routeOption);
        this.restInPeaceTimeout = options.valueOf(timeoutOption);

        val remotes = new HashSet<InetSocketAddress>();
        for (val remote : options.valuesOf(remoteOption))
        {
            val remoteParts = remote.split(":");
            try
            {
                val host = remoteParts[0];
                val port = Integer.valueOf(remoteParts[1]);
                if (!Strings.isNullOrEmpty(host) && port > 0 && port < 65535)
                {
                    remotes.add(new InetSocketAddress(remoteParts[0], port));
                    continue;
                }
            }
            catch (ArrayIndexOutOfBoundsException | NumberFormatException e)
            {
                // throw below
            }

            throw new IllegalArgumentException("invalid host:port pair specified: " + remote);
        }

        this.remotes = Collections.unmodifiableSet(remotes);
    }
}
