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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AsyncInsectWrapper<T extends Insect<?>> implements CloseableRunnable
{
    @Getter
    private final T insect;

    private InsectWorker insectWorker;


    AsyncInsectWrapper(T insect)
    {
        this.insect = insect;
    }


    @Override
    public void run()
    {
        // spawn worker thread
        insectWorker = InsectWorker.start(insect, insect.getClass().getSimpleName());
    }


    @Override
    public void close()
    {
        if (insectWorker.shutdown())
        {
            insectWorker = null;
            log.debug("worker shut down");
        }
    }


    public boolean isRunning()
    {
        return insectWorker != null && insect.isRunning();
    }
}
