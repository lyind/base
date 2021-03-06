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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import lombok.AccessLevel;
import lombok.Getter;


public class BaseMessage
{
    @Getter(AccessLevel.PROTECTED)
    private final ByteBuffer buffer;

    @Getter
    private InetSocketAddress remoteAddress;


    protected BaseMessage(int maximumSize)
    {
        buffer = ByteBuffer.allocateDirect(maximumSize);
    }


    /**
     * Override this to perform additional cleanup.
     */
    protected void clear()
    {

    }


    boolean receiveFrom(DatagramChannel channel) throws IOException
    {
        remoteAddress = (InetSocketAddress) channel.receive(buffer);
        if (remoteAddress != null)
        {
            buffer.flip();
            return true;
        }

        return false;
    }


    void setRemoteAddress(InetSocketAddress remoteAddress)
    {
        this.remoteAddress = remoteAddress;
    }


    boolean sendTo(DatagramChannel channel) throws IOException
    {
        return channel.send(buffer, remoteAddress) != 0;
    }


    void passivate()
    {
        clear();  // may be overridden

        buffer.clear();
        remoteAddress = null;
    }
}
