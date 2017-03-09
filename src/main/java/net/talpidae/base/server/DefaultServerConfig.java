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

package net.talpidae.base.server;

import io.undertow.server.HttpHandler;
import lombok.Getter;
import lombok.Setter;
import net.talpidae.base.resource.JerseyApplication;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;


@Singleton
public class DefaultServerConfig implements ServerConfig
{
    @Setter
    @Getter
    private int port = 0;

    @Setter
    @Getter
    private String host = "127.0.0.1";

    @Setter
    @Getter
    private String[] jerseyResourcePackages = new String[]{JerseyApplication.class.getPackage().getName()};

    @Setter
    @Getter
    private boolean isLoggingFeatureEnabled = true;

    @Setter
    @Getter
    private WebSocketHandler webSocketHandler = null;

    @Setter
    @Getter
    private Set<HttpHandler> additionalHandlers = new HashSet<>();
}