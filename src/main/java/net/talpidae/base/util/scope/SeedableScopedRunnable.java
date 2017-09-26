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

package net.talpidae.base.util.scope;

import com.google.inject.Key;


public interface SeedableScopedRunnable extends Runnable
{
    SeedableScope getSeedableScope();


    /**
     * Seed objects into the relevant scope for this ScopedRunnable.
     */
    <T> SeedableScopedRunnable seed(Key<T> key, T value);


    default <T> SeedableScopedRunnable seed(Class<T> clazz, T value)
    {
        return seed(Key.get(clazz), value);
    }
}
