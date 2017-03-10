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

import lombok.Getter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;


public class BaseMessage
{
    private final ByteBuffer buffer;

    @Getter
    private InetSocketAddress remoteAddress;


    protected BaseMessage(int maximumSize)
    {
        buffer = ByteBuffer.allocate(maximumSize);
    }


    protected ByteBuffer getBuffer()
    {
        return buffer;
    }

    protected void setRemoteAddress(InetSocketAddress remoteAddress)
    {
        this.remoteAddress = remoteAddress;
    }


    public boolean receiveFrom(DatagramChannel channel) throws IOException
    {
        remoteAddress = (InetSocketAddress) channel.receive(buffer);
        if (remoteAddress != null)
        {
            buffer.flip();
            return true;
        }

        return false;
    }


    public boolean sendTo(DatagramChannel channel) throws IOException
    {
        return channel.send(buffer, remoteAddress) != 0;
    }


    protected void clear()
    {
        buffer.clear();
        remoteAddress = null;
    }
}
