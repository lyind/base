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
import net.talpidae.base.server.WebSocketHandler;

import javax.inject.Inject;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;


@ServerEndpoint("/")
public class WebSocketEndpoint
{
    private final WebSocketHandler handler;

    @Inject
    public WebSocketEndpoint(ServerConfig serverConfig)
    {
        this.handler = serverConfig.getWebSocketHandler();
    }

    @OnOpen
    public void connect(Session session) throws IOException { handler.connect(session); }

    @OnMessage
    public void message(String message, Session session) { handler.message(message, session); }

    @OnMessage
    public void message(byte[] data, boolean done, Session session) { handler.message(data, done, session); }

    @OnError
    public void error(Throwable exception, Session session) { handler.error(exception, session); }

    @OnClose
    public void close(CloseReason closeReason, Session session) { handler.close(closeReason, session); }
}
