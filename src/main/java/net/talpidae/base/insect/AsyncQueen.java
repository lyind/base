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

package net.talpidae.base.insect;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.talpidae.base.util.log.LoggingConfigurer;

import javax.inject.Inject;


@Singleton
@Slf4j
public class AsyncQueen extends AsyncInsectWrapper<SyncQueen> implements Queen
{
    @Inject
    public AsyncQueen(SyncQueen syncQueen, LoggingConfigurer loggingConfigurer)
    {
        super(syncQueen, loggingConfigurer);
    }
}
