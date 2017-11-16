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

package net.talpidae.base.util.protocol;

import lombok.val;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;


public class BinaryProtocolHelper
{
    private static final ThreadLocal<Codec> codec = ThreadLocal.withInitial(Codec::new);

    /**
     * Decode and retrieve a UTF-8 encoded char array from the specified offset assuming a maximum size of length bytes.
     */
    public static String extractString(ByteBuffer buffer, int offset, int length) throws CharacterCodingException
    {
        if (length == 0)
        {
            return "";
        }

        val previousPosition = buffer.position();
        val previousLimit = buffer.limit();
        try
        {
            buffer.position(offset);
            buffer.limit(offset + length);

            val output = codec.get().getDecoder().decode(buffer);

            return output.toString();
        }
        finally
        {
            buffer.position(previousPosition);
            buffer.limit(previousLimit);
        }
    }

    /**
     * Put UTF-8 char array at the specified offset (DirectByteBuffer safe) and return size of put data in bytes.
     *
     * @return 0 on empty input, zero or negative maximumSize or output buffer overflow, encoded size otherwise.
     */
    public static int putTruncatedUTF8(ByteBuffer buffer, int offset, String s, int maximumSize)
    {
        val limit = Math.min(buffer.capacity(), offset + maximumSize);
        if (maximumSize <= 0 || s == null || s.isEmpty() || (offset + s.length()) > limit)
            return 0;

        val previousPosition = buffer.position();
        val previousLimit = buffer.limit();
        try
        {
            buffer.position(offset);
            buffer.limit(limit);

            val encoder = codec.get().getEncoder().reset();
            val result = encoder.encode(CharBuffer.wrap(s), buffer, true);
            if (result == CoderResult.OVERFLOW)
            {
                return 0;
            }

            encoder.flush(buffer);

            return buffer.position() - offset;
        }
        finally
        {
            buffer.position(previousPosition);
            buffer.limit(previousLimit);
        }
    }


    /**
     * Use lazy initialization. Some thread may only ever do reading or writing.
     */
    private static final class Codec
    {
        private CharsetEncoder encoder;

        private CharsetDecoder decoder;


        private CharsetEncoder getEncoder()
        {
            if (encoder != null)
                return encoder;

            return encoder = StandardCharsets.UTF_8.newEncoder();
        }


        private CharsetDecoder getDecoder()
        {
            if (decoder != null)
                return decoder;

            return decoder = StandardCharsets.UTF_8.newDecoder();
        }
    }
}
