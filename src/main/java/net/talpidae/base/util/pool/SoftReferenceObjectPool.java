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

package net.talpidae.base.util.pool;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import lombok.val;


/**
 * Maintains a simple, non-thread-safe, hard-limited object pool in a LIFO fashion.
 * <p>
 * Uses SoftReferences to allow GC to collect pooled objects should the need arise.
 */
public class SoftReferenceObjectPool<T>
{
    private final List<SoftReference<T>> pool = new ArrayList<>();

    private final Supplier<T> factory;

    private final int hardLimit;


    public SoftReferenceObjectPool(Supplier<T> factory, int hardLimit)
    {
        this.factory = factory;
        this.hardLimit = hardLimit;
    }


    public T borrow()
    {
        for (int i = pool.size() - 1; i >= 0; --i)
        {
            val thing = pool.remove(i).get();
            if (thing != null)
            {
                return thing;
            }
        }

        return factory.get();
    }


    public void recycle(T thing)
    {
        if (pool.size() < hardLimit)
        {
            pool.add(new SoftReference<>(thing));
        }
    }
}
