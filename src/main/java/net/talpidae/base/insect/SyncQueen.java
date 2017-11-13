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

import net.talpidae.base.insect.config.QueenSettings;
import net.talpidae.base.insect.message.payload.Invalidate;
import net.talpidae.base.insect.message.payload.Mapping;
import net.talpidae.base.insect.message.payload.Shutdown;
import net.talpidae.base.insect.state.InsectState;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Singleton
@Slf4j
public class SyncQueen extends Insect<QueenSettings> implements Queen
{
    @Inject
    public SyncQueen(QueenSettings settings)
    {
        super(settings, false);
    }

    private static Mapping createMappingFromState(InsectState state, String route)
    {
        return Mapping.builder()
                .name(state.getName())
                .host(state.getSocketAddress().getHostString())
                .port(state.getSocketAddress().getPort())
                .timestamp(state.getTimestamp())
                .route(route)
                .socketAddress(state.getSocketAddress())
                .build();
    }

    /**
     * Get a (live) stream of all current service state.
     */
    @Override
    public Stream<InsectState> getLiveInsectState()
    {
        return getRouteToInsects().values()
                .stream()
                .map(InsectCollection::getAll)
                .flatMap(Collection::stream);
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
        getRouteToInsects().getOrDefault(route, emptyRoute())
                .compute(socketAddress, state ->
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

    @Override
    protected void postHandleMapping(InsectState state, Mapping mapping, boolean isNewMapping, boolean isDependencyMapping)
    {
        if (isNewMapping)
        {
            sendInvalidate(state.getSocketAddress());
        }

        if (isDependencyMapping)
        {
            handleDependencyRequest(state, mapping);
        }
        else
        {
            relayMapping(state, mapping);
        }
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
     * Immediately respond with one of the mappings we have in case a dependency was published.
     * Otherwise the sender would have to wait for the next pulse to reach it.
     */
    private void handleDependencyRequest(InsectState state, Mapping mapping)
    {
        val alternatives = getRouteToInsects().getOrDefault(mapping.getDependency(), emptyRoute()).getActive();
        if (!alternatives.isEmpty())
        {
            // find random non out-of-service insect
            val size = alternatives.size();
            val startIndex = getRandom().nextInt(alternatives.size());
            for (int i = 0; i < size; ++i)
            {
                val candidate = alternatives.get((i + startIndex) % size);
                if (!candidate.isOutOfService())
                {
                    addMessage(candidate.getSocketAddress(), createMappingFromState(candidate, mapping.getDependency()));
                }
            }
        }
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

        getRouteToInsects().forEach((route, states) ->
        {
            val mappingRoute = mapping.getRoute();
            if (route != null)
            {
                for (val s : states.getActive())
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
