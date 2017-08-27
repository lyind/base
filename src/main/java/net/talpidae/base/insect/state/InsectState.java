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

package net.talpidae.base.insect.state;

import java.net.InetSocketAddress;
import java.util.Set;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import lombok.val;


/**
 * Things the queen knows about each insect.
 */
@Getter
@EqualsAndHashCode
@ToString
@Builder
public class InsectState implements ServiceState
{
    /**
     * InetSocketAddress representing host and port.
     */
    @Getter
    private final InetSocketAddress socketAddress;

    /**
     * This service instance's unique name.
     */
    @Getter
    private final transient String name;

    /**
     * Service monotonic clock timestamp epoch (remote).
     */
    @Getter
    private final transient long timestampEpochRemote;

    /**
     * Service monotonic clock timestamp epoch (local).
     */
    @Getter
    private final transient long timestampEpochLocal;

    /**
     * Service monotonic clock timestamp (relative to timestampEpoch).
     */
    @Getter
    private final transient long timestamp;

    /**
     * Routes of other services this service depends on.
     */
    @Getter
    @Singular
    private final transient Set<String> dependencies;


    public static class InsectStateBuilder
    {
        public InsectStateBuilder newEpoch(long remoteTimestampEpoch)
        {
            val now = System.nanoTime();
            return timestampEpochLocal(now)
                    .timestampEpochRemote(remoteTimestampEpoch)
                    .timestamp(now);
        }
    }
}
