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

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
@Builder
public class Invalidate extends Payload
{
    public static final int MAXIMUM_SERIALIZED_SIZE = 2;

    public static final int TYPE_INVALIDATE = 0x3;

    public static final int MAGIC = 0x73;

    @Getter
    @Builder.Default
    private final int type = TYPE_INVALIDATE;    // 0x3: invalidate known remotes

    @Getter
    @Builder.Default
    private final int magic = MAGIC;           // magic byte: 0x73


    static Invalidate from(ByteBuffer buffer, int offset) throws IndexOutOfBoundsException
    {
        val type = buffer.get(offset) & 0xFF;
        if (type != TYPE_INVALIDATE)
        {
            return null;
        }

        val magic = buffer.get(offset + 1) & 0xFF;
        if (magic != MAGIC)
        {
            log.debug("encountered invalidate payload with invalid magic");
            return null;
        }

        return Invalidate.builder()
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
}