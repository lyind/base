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
import com.google.common.net.HostAndPort;
import com.google.inject.Singleton;

import net.talpidae.base.server.ServerConfig;
import net.talpidae.base.util.BaseArguments;
import net.talpidae.base.util.log.LoggingConfigurer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static net.talpidae.base.util.log.LoggingConfigurer.CONTEXT_INSECT_NAME_KEY;


@Singleton
@Setter
@Getter
@Slf4j
public class DefaultSlaveSettings implements SlaveSettings
{
    @Setter
    @Getter
    private String name;

    @NonNull
    private InetSocketAddress bindAddress;

    @NonNull
    private Set<InetSocketAddress> remotes;

    @NonNull
    private String route;

    private long pulseDelay = DEFAULT_PULSE_DELAY;

    private long restInPeaceTimeout;


    @Inject
    public DefaultSlaveSettings(ServerConfig serverConfig, BaseArguments baseArguments, LoggingConfigurer loggingConfigurer)
    {
        this.bindAddress = new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort());

        val parser = baseArguments.getOptionParser();
        val nameOption = parser.accepts("insect.name").withRequiredArg().required();
        val remoteOption = parser.accepts("insect.slave.remote").withRequiredArg().required();
        val timeoutOption = parser.accepts("insect.slave.timeout").withRequiredArg().ofType(Long.class).defaultsTo(DEFAULT_REST_IN_PEACE_TIMEOUT);
        val options = baseArguments.parse();

        this.name = options.valueOf(nameOption).intern();
        this.restInPeaceTimeout = options.valueOf(timeoutOption);

        val remotes = new HashSet<InetSocketAddress>();
        for (val remoteValue : options.valuesOf(remoteOption))
        {
            val remote = HostAndPort.fromString(remoteValue);
            try
            {
                val host = remote.getHost();
                val port = remote.getPortOrDefault(QueenSettings.DEFAULT_PORT);
                if (!Strings.isNullOrEmpty(host))
                {
                    val socketAddress = new InetSocketAddress(host.intern(), port);
                    if (socketAddress.isUnresolved())
                    {
                        throw new IllegalArgumentException("failed to resolve remote host: " + socketAddress.getHostString());
                    }

                    remotes.add(socketAddress);
                    continue;
                }
            }
            catch (ArrayIndexOutOfBoundsException | NumberFormatException e)
            {
                // throw below
            }

            throw new IllegalArgumentException("invalid host[:port] pair specified: " + remoteValue);
        }

        this.remotes = Collections.unmodifiableSet(remotes);

        loggingConfigurer.putContext(CONTEXT_INSECT_NAME_KEY, name);
    }
}