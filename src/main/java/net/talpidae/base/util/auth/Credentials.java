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

package net.talpidae.base.util.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.val;


@NoArgsConstructor
public class Credentials
{
    @Getter
    @Setter
    private String name;

    private CharSequence password;


    @Builder
    @JsonCreator
    public Credentials(@JsonProperty("name") String name, @JsonProperty("password") CharSequence password)
    {
        this.name = name;
        setPassword(password);
    }

    public CharSequence getPassword()
    {
        return password;
    }

    /**
     * We can't do anything about the initial string, but hope that it is overwritten quite fast.
     * Afterwards we have full control and store the password chars in a DirectCharBuffer that we can manually wipe.
     */
    public void setPassword(CharSequence password)
    {
        clear();

        if (password != null)
        {
            val buffer = ByteBuffer.allocateDirect(password.length() * 2).asCharBuffer();

            for (int i = 0; i < password.length(); ++i)
            {
                buffer.put(i, password.charAt(i));
            }

            this.password = new CharBufferAsCharSequence(buffer);
        }
    }

    @JsonProperty("password")
    public CharSequence getPasswordDeepClone()
    {
        val length = (password != null) ? password.length() : 0;
        val builder = new StringBuilder(length);
        for (int i = 0; i < length; ++i)
        {
            builder.append(password.charAt(i));
        }

        return builder.toString();
    }


    public void clear()
    {
        if (password instanceof CharBufferAsCharSequence)
        {
            ((CharBufferAsCharSequence) password).clear();
        }

        password = null;
    }


    private static class CharBufferAsCharSequence implements CharSequence
    {
        private final CharBuffer buffer;

        CharBufferAsCharSequence(CharBuffer buffer)
        {
            this.buffer = buffer;
        }


        void clear()
        {
            for (int i = 0; i < buffer.capacity(); ++i)
            {
                buffer.put(i, '\0');
            }
        }

        @Override
        public String toString()
        {
            return buffer.toString();
        }

        @Override
        public int length()
        {
            return buffer.capacity();
        }

        @Override
        public char charAt(int index)
        {
            return buffer.get(index);
        }

        @Override
        public CharSequence subSequence(int start, int end)
        {
            return new CharBufferAsCharSequence(buffer.subSequence(start, end));
        }
    }
}
