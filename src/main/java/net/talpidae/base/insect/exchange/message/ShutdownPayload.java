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

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.nio.ByteBuffer;


@Slf4j
@Builder
public class ShutdownPayload implements Payload
{
    public static final int MAXIMUM_SERIALIZED_SIZE = 2;

    public static final int TYPE_SHUTDOWN = 0x2;

    public static final int MAGIC = 0x86;

    @Getter
    private final int type;           // 0x2: shutdown

    @Getter
    private final int magic;          // magic byte: 0x86


    static ShutdownPayload from(ByteBuffer buffer, int offset) throws IndexOutOfBoundsException
    {
        val type = buffer.get(offset) & 0xFF;
        if (type != TYPE_SHUTDOWN)
        {
            return null;
        }

        val magic = buffer.get(offset + 1) & 0xFF;
        if (magic != 0x86)
        {
            log.debug("encountered shutdown payload with invalid magic");
            return null;
        }

        return ShutdownPayload.builder()
                .type(type)
                .magic(magic)
                .build();
    }


    public void to(ByteBuffer buffer)
    {
        buffer.put((byte) type);
        buffer.put((byte) magic);
    }


    @Override
    public int getMaximumSize()
    {
        return MAXIMUM_SERIALIZED_SIZE;
    }


    @Override
    public String toString()
    {
        return Integer.toHexString(getType());
    }


    /**
     * Base class for lombok builder.
     */
    public static class ShutdownPayloadBuilder
    {
        private int type = TYPE_SHUTDOWN;
    }
}