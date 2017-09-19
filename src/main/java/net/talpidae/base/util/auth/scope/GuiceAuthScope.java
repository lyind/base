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
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import lombok.val;
import net.talpidae.base.util.scope.SeedableScope;

import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;


public class GuiceAuthScope implements SeedableScope
{
    private AtomicReference<AuthScope> authScope = new AtomicReference<>(null);


    public void enter(AuthScope authScope)
    {
        checkState(this.authScope.compareAndSet(null, authScope), "A scoping block is already in progress");
    }


    public void exit()
    {
        checkState(authScope.get() != null, "No scoping block in progress");
        authScope.set(null);
    }


    public <T> void seed(Key<T> key, T value)
    {
        val scope = authScope.get();
        if (scope == null)
        {
            throw new OutOfScopeException("Cannot seed " + key + " outside of a scoping block");
        }

        checkState(!scope.containsKey(key), "A value for the key %s was " +
                        "already seeded in this scope. Old value: %s New value: %s", key,
                scope.get(key), value);
        scope.put(key, value);
    }


    public <T> void seed(Class<T> clazz, T value)
    {
        seed(Key.get(clazz), value);
    }


    public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscoped)
    {
        return () ->
        {
            val scope = authScope.get();
            if (scope == null)
            {
                return null;
            }

            T current = (T) scope.get(key);
            if (current == null && !scope.containsKey(key))
            {
                current = unscoped.get();

                // proxies exist only to serve circular dependencies
                if (Scopes.isCircularProxy(current))
                {
                    return current;
                }

                scope.put(key, current);
            }

            return current;
        };
    }


    @Override
    public String toString()
    {
        return "BaseScopes.AUTH";
    }
}