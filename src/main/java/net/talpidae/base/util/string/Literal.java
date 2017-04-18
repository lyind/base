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

package net.talpidae.base.util.string;

import lombok.SneakyThrows;
import lombok.val;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


public final class Literal
{
    /**
     * Load resources as String literals. Consider this a workaround for non-existing Java multi-line strings.
     *
     * @param path The path of the String literal resource to use ("/blabla/resource.txt" for "/src/main/resources/blabla/resource.txt").
     * @return The String content of the specified resource.
     */
    @SneakyThrows
    public static String from(String path)
    {
        val inputStream = Literal.class.getResourceAsStream(path);
        if (inputStream == null)
            throw new ClassNotFoundException("Resource not found: " + path);

        return readAll(inputStream);
    }


    private static String readAll(InputStream inputStream) throws IOException
    {
        val builder = new StringBuilder();
        val reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        val buffer = new char[512];

        while (true)
        {
            val readCount = reader.read(buffer);
            if (readCount <= 0)
                break;

            builder.append(buffer, 0, readCount);
        }

        return builder.toString();
    }


    private Literal()
    {

    }
}
