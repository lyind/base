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

package net.talpidae.base.util.auth.scope;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;

import lombok.val;


public class GuiceAuthScope implements Scope
{
    private AuthScope current = null;


    @Override
    public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscoped)
    {
        return () -> {
            final T instance;
            if (current != null)
            {
                val existingInstance = (T) current.get(key);
                if (existingInstance != null)
                {
                    instance = existingInstance;
                }
                else
                {
                    instance = unscoped.get();
                    current.set(key, instance);
                }
            }
            else
            {
                instance = unscoped.get();
            }

            return instance;
        };
    }

    public void enter(AuthScope scope)
    {
        current = scope;
    }

    public void leave()
    {
        current = null;
    }


    @Override
    public String toString()
    {
        return "BaseScopes.AUTH";
    }
}