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

package net.talpidae.base.insect.message.payload;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;


public final class PayloadFactory
{
    private PayloadFactory()
    {

    }

    /**
     * Identify and unpack the content stored inside buffer (@offset).
     */
    public static Payload unpackPayload(ByteBuffer buffer, int offset) throws IndexOutOfBoundsException, CharacterCodingException
    {
        Payload payload;

        // probe message types (most frequent first)
        if ((payload = Metrics.from(buffer, offset)) != null
                || (payload = Mapping.from(buffer, offset)) != null
                || (payload = Invalidate.from(buffer, offset)) != null
                || (payload = Shutdown.from(buffer, offset)) != null)
        {
            return payload;
        }

        return null;
    }


    /**
     * Get maximum buffer size needed to accommodate all known message types.
     */
    public static int getMaximumSerializedSize()
    {
        return Math.max(Metrics.MAXIMUM_SERIALIZED_SIZE,
                Math.max(Mapping.MAXIMUM_SERIALIZED_SIZE,
                        Math.max(Invalidate.MAXIMUM_SERIALIZED_SIZE, Shutdown.MAXIMUM_SERIALIZED_SIZE)));
    }
}
