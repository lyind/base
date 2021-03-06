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

import net.talpidae.base.util.scope.SeedableScope;
import net.talpidae.base.util.scope.SeedableScopedRunnable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@AllArgsConstructor
public class AuthenticatedRunnable extends AuthScope implements SeedableScopedRunnable
{
    private final GuiceAuthScope guiceAuthScope;

    @Getter
    private final Runnable runnable;


    @Override
    public void run()
    {
        try
        {
            guiceAuthScope.enter(this);
            try
            {
                runnable.run();
            }
            finally
            {
                guiceAuthScope.exit();
            }
        }
        catch (RuntimeException e)
        {
            log.warn("exception in {}: {}", runnable.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }


    @Override
    public SeedableScope getSeedableScope()
    {
        return guiceAuthScope;
    }


    @Override
    public <T> SeedableScopedRunnable seed(Key<T> key, T value)
    {
        put(key, value);

        return this;
    }
}
