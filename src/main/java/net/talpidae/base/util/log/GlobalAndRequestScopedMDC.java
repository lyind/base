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

package net.talpidae.base.util.log;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import lombok.val;


/**
 * Custom MDC lookalike with global and thread-local parts.
 */
@SuppressWarnings("unused")
public class GlobalAndRequestScopedMDC
{
    private static final AtomicReference<Map<String, String>> GLOBAL_CONTEXT = new AtomicReference<>(ImmutableMap.of());

    // TODO Add second level (request scoped or thread-local)


    public static void putGlobal(String key, String value)
    {
        Map<String, String> current;
        Map<String, String> next;
        do
        {
            current = GLOBAL_CONTEXT.get();

            next = ImmutableMap.<String, String>builderWithExpectedSize(current.size() + 1)
                    .putAll(current)
                    .put(key, value)
                    .build();
        }
        while (!GLOBAL_CONTEXT.compareAndSet(current, next));
    }


    public static void removeGlobal(String key)
    {
        Map<String, String> current;
        Map<String, String> next;
        do
        {
            current = GLOBAL_CONTEXT.get();

            if (!current.containsKey(key))
            {
                // nothing to do
                return;
            }

            // filter map, removing the entry with the specified key (if present)
            val builder = ImmutableMap.<String, String>builderWithExpectedSize(current.size());
            for (val entry : current.entrySet())
            {
                builder.put(entry);
            }

            next = builder.build();
        }
        while (!GLOBAL_CONTEXT.compareAndSet(current, next));
    }


    public static String getGlobal(String key)
    {
        return GLOBAL_CONTEXT.get().get(key);
    }
}
