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

import lombok.val;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


public interface Payload
{
    static String extractString(ByteBuffer buffer, int offset, int length)
    {
        if (length == 0)
        {
            return "";
        }

        return new String(buffer.array(), buffer.arrayOffset() + offset, length, StandardCharsets.UTF_8);
    }

    static byte[] toTruncatedUTF8(String s, int maximumSize)
    {
        val chars = s.substring(0, Math.min(s.length(), maximumSize + 1)).getBytes(StandardCharsets.UTF_8);
        val length = chars.length;
        for (int i = length - 1; i >= 0; --i)
        {
            if ((chars[i] & 0xC0) == 0xC0)
            {
                // found start byte of the last UTF-8 character
                if (((chars[i] & 0xE0) == 0xC0 && i + 1 >= length)     // truncated 2 byte char
                        || ((chars[i] & 0xF0) == 0xE0 && i + 2 >= length)  // truncated 3 byte char
                        || ((chars[i] & 0xF8) == 0xF0 && i + 3 >= length)) // truncated 4 byte char
                {
                    return Arrays.copyOf(chars, i);
                }
            }
        }

        return chars;
    }

    void to(ByteBuffer buffer);

    int getMaximumSize();

    String toString();
}
