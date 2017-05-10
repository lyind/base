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

import net.talpidae.base.insect.state.ServiceState;

import java.net.InetSocketAddress;
import java.util.List;


public interface Slave extends CloseableRunnable
{
    /**
     * Try to find a service for route, register route as a dependency and block in case it isn't available immediately.
     */
    InetSocketAddress findService(String route) throws InterruptedException;

    /**
     * Try to find a service for route, register route as a dependency and block in case it isn't available immediately.
     *
     * @return Address of discovered service if one was discovered before a timeout occurred, null otherwise.
     */
    InetSocketAddress findService(String route, long timeoutMillies) throws InterruptedException;

    /**
     * Return all known services for route, register route as a dependency and block in case there are none available immediately.
     *
     * @return Discovered services if any were discovered before a timeout occurred, null otherwise.
     */
    List<? extends ServiceState> findServices(String route, long timeoutMillies) throws InterruptedException;
}
