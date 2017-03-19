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

import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.io.IOException;


public interface WebSocketHandler
{
    void connect(Session session) throws IOException;

    void message(String message, Session session);

    void message(byte[] data, boolean done, Session session);

    void error(Throwable exception, Session session);

    void close(CloseReason closeReason, Session session);
}
