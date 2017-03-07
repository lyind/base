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
import net.talpidae.base.server.ServerConfig;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;


@Singleton
@Getter
public class DefaultQueenSettings implements QueenSettings
{
    private final InetSocketAddress bindAddress;

    private final Set<InetSocketAddress> remotes = Collections.emptySet();

    private final long restInPeaceTimeout = DEFAULT_REST_IN_PEACE_TIMEOUT;

    @Inject
    public DefaultQueenSettings(ServerConfig serverConfig)
    {
        bindAddress = new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort());
    }
}
