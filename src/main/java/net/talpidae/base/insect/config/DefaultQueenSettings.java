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
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.talpidae.base.server.ServerConfig;
import net.talpidae.base.util.log.LoggingConfigurer;
import net.talpidae.base.util.names.InsectNameGenerator;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;

import static net.talpidae.base.util.log.LoggingConfigurer.CONTEXT_INSECT_NAME_KEY;


@Slf4j
@Singleton
@Setter
@Getter
public class DefaultQueenSettings implements QueenSettings
{
    @Setter
    @Getter
    private String name;

    @NonNull
    private InetSocketAddress bindAddress;

    @NonNull
    private Set<InetSocketAddress> remotes;

    private long pulseDelay = DEFAULT_PULSE_DELAY;

    private long restInPeaceTimeout = DEFAULT_REST_IN_PEACE_TIMEOUT;

    @Inject
    public DefaultQueenSettings(ServerConfig serverConfig, LoggingConfigurer loggingConfigurer, InsectNameGenerator insectNameGenerator)
    {
        name = insectNameGenerator.compose().replace(' ', '-').intern();
        bindAddress = new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort());
        remotes = Collections.emptySet();

        loggingConfigurer.putContext(CONTEXT_INSECT_NAME_KEY, name);
    }
}
