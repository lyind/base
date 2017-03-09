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
import net.talpidae.base.insect.config.SlaveSettings;

import javax.inject.Inject;


@Singleton
@Slf4j
public class AsynchronousSlave extends SynchronousSlave
{
    private static final long SLAVE_TIMEOUT = 4000;

    private Thread slaveWorker = null;


    @Inject
    public AsynchronousSlave(SlaveSettings settings)
    {
        super(settings);
    }


    @Override
    public void run()
    {
        // spawn slave thread
        slaveWorker = startWorker(() -> super.run(), "Insect-AsynchronousSlave", SLAVE_TIMEOUT);
    }


    @Override
    public void close()
    {
        super.close();

        if (joinWorker(slaveWorker, SLAVE_TIMEOUT))
        {
            slaveWorker = null;
            log.debug("slave worker shut down");
        }
        else
        {
            log.warn("failed to shut down slave worker");
        }
    }
}
