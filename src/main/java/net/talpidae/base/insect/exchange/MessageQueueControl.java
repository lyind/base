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

package net.talpidae.base.insect.exchange;

import java.net.InetSocketAddress;


public interface MessageQueueControl<M extends BaseMessage>
{
    /**
     * Create and add a new outbound message.
     */
    M addOutbound(InetSocketAddress remoteAddress);


    /**
     * Poll for new inbound messages.
     *
     * @return Reference to an inbound message, valid until the next call to pollInbound().
     * Returns null if there are no inbound messages.
     */
    M pollInbound();
}