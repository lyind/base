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

package net.talpidae.base.insect.exchange.message;

import java.nio.ByteBuffer;


public class PayloadFactory
{
    /**
     * Identify and unpack the content stored inside buffer (@offset).
     */
    public static Payload unpackPayload(ByteBuffer buffer, int offset) throws IndexOutOfBoundsException
    {
        Payload payload;

        // probe message types
        if ((payload = MappingPayload.from(buffer, offset)) != null
                || (payload = ShutdownPayload.from(buffer, offset)) != null)
        {
            return payload;
        }

        return null;
    }
}
