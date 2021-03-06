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

import net.talpidae.base.insect.state.InsectState;
import net.talpidae.base.util.log.LoggingConfigurer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.stream.Stream;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;


@Singleton
@Slf4j
public class AsyncQueen extends AsyncInsectWrapper<SyncQueen> implements Queen
{
    @Inject
    public AsyncQueen(SyncQueen syncQueen, LoggingConfigurer loggingConfigurer)
    {
        super(syncQueen, loggingConfigurer);
    }

    @Override
    public void initializeInsectState(Stream<Map.Entry<String, InsectState>> stateStream)
    {
        getInsect().initializeInsectState(stateStream);
    }

    @Override
    public Stream<InsectState> getLiveInsectState()
    {
        return getInsect().getLiveInsectState();
    }

    @Override
    public void sendShutdown(InetSocketAddress remote)
    {
        getInsect().sendShutdown(remote);
    }

    @Override
    public void setIsOutOfService(String route, InetSocketAddress socketAddress, boolean isOutOfService)
    {
        getInsect().setIsOutOfService(route, socketAddress, isOutOfService);
    }
}
