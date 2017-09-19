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

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.talpidae.base.util.scope.SeedableScope;
import net.talpidae.base.util.scope.SeedableScopedRunnable;


@AllArgsConstructor
public class AuthenticatedRunnable implements SeedableScopedRunnable
{
    private final AuthScope authScope = new AuthScope();

    private final GuiceAuthScope guiceAuthScope;

    @Getter
    private final Runnable runnable;


    @Override
    public void run()
    {
        guiceAuthScope.enter(authScope);
        try
        {
            runnable.run();
        }
        finally
        {
            guiceAuthScope.exit();
        }
    }

    @Override
    public SeedableScope getSeedableScope()
    {
        return guiceAuthScope;
    }
}