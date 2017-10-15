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
import net.talpidae.base.insect.message.payload.Invalidate;
import net.talpidae.base.insect.message.payload.Mapping;
import net.talpidae.base.insect.message.payload.Shutdown;
import net.talpidae.base.insect.state.InsectState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.stream.Stream;


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
    protected void postHandleMapping(InsectState state, Mapping mapping, boolean isNewMapping)
    {
        if (isNewMapping)
        {
            sendInvalidate(state.getSocketAddress());
        }

        relayMapping(state, mapping);
    }


    /**
     * Get a (live) stream of all current service state.
     */
    @Override
    public Stream<InsectState> getLiveInsectState()
    {
        return getRouteToInsects().values()
                .stream()
                .map(InsectCollection::getInsects)
                .flatMap(Arrays::stream);
    }


    /**
     * Send an invalidate request to a slave.
     */
    private void sendInvalidate(InetSocketAddress remote)
    {
        val invalidate = (Invalidate) Invalidate.builder()
                .type(Invalidate.TYPE_INVALIDATE)
                .magic(Invalidate.MAGIC)
                .build();

        addMessage(remote, invalidate);
    }


    /**
     * Send a shutdown request to a slave.
     */
    @Override
    public void sendShutdown(InetSocketAddress remote)
    {
        val shutdown = (Shutdown) Shutdown.builder()
                .type(Shutdown.TYPE_SHUTDOWN)
                .magic(Shutdown.MAGIC)
                .build();

        addMessage(remote, shutdown);
    }


    @Override
    public void setIsOutOfService(String route, InetSocketAddress socketAddress, boolean isOutOfService)
    {
        getRouteToInsects().getOrDefault(route, EMPTY_ROUTE)
                .update(socketAddress, state ->
                        (state != null) ?
                                // copy everything but the isOutOfService flag
                                InsectState.builder()
                                        .name(state.getName())
                                        .isOutOfService(isOutOfService)
                                        .timestampEpochRemote(state.getTimestampEpochRemote())
                                        .timestamp(state.getTimestamp())
                                        .timestampEpochLocal(state.getTimestampEpochLocal())
                                        .dependencies(state.getDependencies())
                                        .socketAddress(state.getSocketAddress())
                                        .build()
                                : null
                );
    }


    /**
     * Relay updates to all interested services (those that have this services route in their dependencies).
     */
    private void relayMapping(final InsectState state, final Mapping mapping)
    {
        if (state.isOutOfService())
        {
            // do relay mappings for out-of-service services
            return;
        }
        else if (!Strings.isNullOrEmpty(mapping.getDependency()))
        {
            // do not relay dependency map update packages (mostly done on service startup anyways)
            return;
        }

        getRouteToInsects().forEach((route, states) ->
        {
            val mappingRoute = mapping.getRoute();
            if (route != null)
            {
                for (val s : states.getInsects())
                {
                    if (s.getDependencies().contains(mappingRoute))
                    {
                        val destination = s.getSocketAddress();

                        if (destination.isUnresolved())
                        {
                            log.debug("unresolved address {}", destination);
                        }

                        addMessage(destination, mapping);
                    }
                }
            }
        });
    }
}
