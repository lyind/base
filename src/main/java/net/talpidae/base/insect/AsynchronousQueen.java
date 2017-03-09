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

import lombok.extern.slf4j.Slf4j;
import net.talpidae.base.insect.config.QueenSettings;

import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
@Slf4j
public class AsynchronousQueen extends SynchronousQueen
{
    private static final long QUEEN_TIMEOUT = 5000;

    private Thread queenWorker = null;


    @Inject
    public AsynchronousQueen(QueenSettings settings)
    {
        super(settings);
    }


    @Override
    public void run()
    {
        // spawn queen thread
        queenWorker = startWorker(() -> super.run(), "Insect-AsynchronousQueen", QUEEN_TIMEOUT);
    }


    @Override
    public void close()
    {
        super.close();

        if (joinWorker(queenWorker, QUEEN_TIMEOUT))
        {
            queenWorker = null;
            log.debug("queen worker shut down");
        }
        else
        {
            log.warn("failed to shut down queen worker");
        }
    }
}
