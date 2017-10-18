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

import net.talpidae.base.insect.state.ServiceState;
import net.talpidae.base.util.log.LoggingConfigurer;
import net.talpidae.base.util.performance.Metric;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Queue;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;


@Singleton
@Slf4j
public class AsyncSlave extends AsyncInsectWrapper<SyncSlave> implements Slave
{
    @Inject
    public AsyncSlave(SyncSlave syncSlave, LoggingConfigurer loggingConfigurer)
    {
        super(syncSlave, loggingConfigurer);
    }


    @Override
    public InetSocketAddress findService(String route) throws InterruptedException
    {
        return getInsect().findService(route);
    }

    @Override
    public InetSocketAddress findService(String route, long timeoutMillies) throws InterruptedException
    {
        return getInsect().findService(route, timeoutMillies);
    }

    @Override
    public List<? extends ServiceState> findServices(String route, long timeoutMillies) throws InterruptedException
    {
        return getInsect().findServices(route, timeoutMillies);
    }

    @Override
    public void forwardMetrics(Queue<Metric> metricQueue)
    {
        getInsect().forwardMetrics(metricQueue);
    }
}
