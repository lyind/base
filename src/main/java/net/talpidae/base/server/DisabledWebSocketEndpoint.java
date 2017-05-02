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

import javax.inject.Singleton;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;


@Singleton
@ServerEndpoint("/")
public class DisabledWebSocketEndpoint extends WebSocketEndpoint
{
    public DisabledWebSocketEndpoint()
    {
    }

    @Override
    public void connect(Session session) throws IOException
    {
        session.close();
    }

    @Override
    public void message(String message, Session session)
    {

    }

    @Override
    public void message(byte[] data, boolean done, Session session)
    {

    }

    @Override
    public void error(Throwable exception, Session session)
    {

    }

    @Override
    public void close(CloseReason closeReason, Session session)
    {

    }
}
