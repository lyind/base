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

import com.google.common.base.Strings;
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
        val input = Strings.nullToEmpty(s);
        byte[] chars = input.substring(0, Math.min(input.length(), maximumSize)).getBytes(StandardCharsets.UTF_8);
        if (chars.length <= maximumSize)
            return chars;

        chars = Arrays.copyOf(chars, maximumSize);
        val length = chars.length;
        if ((chars[length - 1] & 0xE0) == 0xC0)
        {
            // partial 2 byte char (only 1 byte present)
            return Arrays.copyOf(chars, length - 1);
        }
        else if ((chars[length - 2] & 0xF0) == 0xE0)
        {
            // partial 3 byte char
            return Arrays.copyOf(chars, length - 2);
        }
        else if ((chars[length - 3] & 0xF8) == 0xF0)
        {
            // partial 4 byte char
            return Arrays.copyOf(chars, length - 3);
        }

        return chars;
    }

    void to(ByteBuffer buffer);

    int getMaximumSize();

    String toString();
}
