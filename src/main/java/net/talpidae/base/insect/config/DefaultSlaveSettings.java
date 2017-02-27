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

import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.server.ServerConfig;
import net.talpidae.base.util.BaseArguments;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;


@Singleton
@Getter
@Slf4j
public class DefaultSlaveSettings implements SlaveSettings
{
    public static final String INSECT_LONG_OPTION = "--insect.remote";

    private final InetSocketAddress bindAddress;

    private final Set<InetSocketAddress> remotes = Collections.emptySet();


    @Inject
    public DefaultSlaveSettings(ServerConfig serverConfig, BaseArguments baseArguments)
    {
        bindAddress = new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort());

        boolean wantNextArg = false;
        for (String arg : baseArguments.getArguments())
        {
            if (wantNextArg)
            {
                wantNextArg = false;
                if (!arg.startsWith("-") && (arg.isEmpty() || arg.contains(":")))
                {
                    addRemotesFromArgument(arg);
                }
                else
                {
                    throw new IllegalArgumentException("expected list of remote host:port pairs but got next argument: " + arg);
                }
            }
            else if (arg.startsWith(INSECT_LONG_OPTION))
            {
                if (arg.startsWith(INSECT_LONG_OPTION + "="))
                {
                    addRemotesFromArgument(arg.replace(INSECT_LONG_OPTION + "=", ""));
                }
                else
                {
                    wantNextArg = true;
                }
            }
        }
    }


    private void addRemotesFromArgument(String value)
    {
        for (String remote : value.split(","))
        {
            remote = remote.trim();
            if (!remote.isEmpty())
            {
                val remotePair = remote.split(":");

                if (remotePair.length >= 2)
                {
                    val host = remotePair[0];
                    try
                    {
                        val port = Integer.valueOf(remotePair[1]);

                        if (!host.isEmpty() && port > 0 && port < 65536)
                        {
                            remotes.add(new InetSocketAddress(host, port));

                            // next argument
                            continue;
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        // triggers warning message below
                    }
                }

                log.warn("expected host:port pair, skipped invalid argument: {}", remote);
            }
        }
    }
}
