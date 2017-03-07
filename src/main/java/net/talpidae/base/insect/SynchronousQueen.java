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

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import net.talpidae.base.insect.config.QueenSettings;
import net.talpidae.base.insect.exchange.message.MappingPayload;
import net.talpidae.base.insect.state.InsectState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Singleton
@Slf4j
public class SynchronousQueen extends Insect<QueenSettings> implements Queen
{
    @Inject
    public SynchronousQueen(QueenSettings settings)
    {
        super(settings, false);
    }


    private static Map<InsectState, InsectState> newInsectStates(String route)
    {
        return new ConcurrentHashMap<>();
    }


    @Override
    protected void postHandleMapping(MappingPayload mapping)
    {
        relayMapping(mapping);
    }


    /**
     * Relay updates to all interested services (those that have this services route in their dependencies).
     */
    private void relayMapping(final MappingPayload mapping)
    {
        if (!Strings.isNullOrEmpty(mapping.getDependency()))
        {
            // do not relay dependency map update packages (mostly done on service startup anyways)
            return;
        }

        getRouteToInsects().forEach((route, states) ->
        {
            if (route != null && !route.equals(mapping.getRoute()))
            {
                states.forEach((stateKey, stateValue) ->
                {
                    if (stateValue.getDependencies().contains(route))
                    {
                        addMessage(new InetSocketAddress(stateValue.getHost(), stateValue.getPort()), mapping);
                    }
                });
            }
        });
    }
}
