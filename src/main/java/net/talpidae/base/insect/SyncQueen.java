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
import lombok.val;
import net.talpidae.base.insect.config.QueenSettings;
import net.talpidae.base.insect.message.payload.Mapping;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetSocketAddress;


@Singleton
@Slf4j
public class SyncQueen extends Insect<QueenSettings> implements Queen
{
    @Inject
    public SyncQueen(QueenSettings settings)
    {
        super(settings, false);
    }


    @Override
    protected void postHandleMapping(Mapping mapping)
    {
        relayMapping(mapping);
    }


    /**
     * Relay updates to all interested services (those that have this services route in their dependencies).
     */
    private void relayMapping(final Mapping mapping)
    {
        if (!Strings.isNullOrEmpty(mapping.getDependency()))
        {
            // do not relay dependency map update packages (mostly done on service startup anyways)
            return;
        }

        getRouteToInsects().forEach((route, states) ->
        {
            val mappingRoute = mapping.getRoute();
            if (route != null && !route.equals(mappingRoute))
            {
                states.forEach((stateKey, stateValue) ->
                {
                    if (stateValue.getDependencies().contains(mappingRoute))
                    {
                        val destination = stateValue.getSocketAddress();

                        if (destination.isUnresolved())
                        {
                            log.debug("unresolved address {}", destination);
                        }

                        addMessage(destination, mapping);
                    }
                });
            }
        });
    }
}
