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

package net.talpidae.base.insect.config;

import java.net.InetSocketAddress;
import java.util.Set;


public interface InsectSettings
{
    long DEFAULT_PULSE_DELAY = 1001;  // 1001ms
    long DEFAULT_REST_IN_PEACE_TIMEOUT = 5 * 60 * 1000;  // 5 min

    /**
     * InetSocketAddress to bind to.
     */
    InetSocketAddress getBindAddress();

    void setBindAddress(InetSocketAddress bindAddress);

    /**
     * Remote servers that are authorized to update mappings they do not own themselves
     * and are informed about our services.
     */
    Set<InetSocketAddress> getRemotes();

    void setRemotes(Set<InetSocketAddress> remotes);

    /**
     * Heart beat / pulse delay.
     */
    long getPulseDelay();

    void setPulseDelay(long pulseDelay);

    /**
     * Timeout after which a service is declared dead and purged from the mapping.
     * Since we always pick the youngest per route, this can be pretty high and will define
     * the maximum permitted down-time of upstream servers (Queen instances).
     */
    long getRestInPeaceTimeout();

    void setRestInPeaceTimeout(long restInPeaceTimeout);
}
