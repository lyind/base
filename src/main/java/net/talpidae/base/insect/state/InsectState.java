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

import lombok.*;

import java.net.InetSocketAddress;
import java.util.Set;


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
     * Service monotonic clock timestamp.
     */
    @Getter
    private final transient long timestamp;

    /**
     * Routes of other services this service depends on.
     */
    @Getter
    @Singular
    private final transient Set<String> dependencies;
}
