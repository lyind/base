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

package net.talpidae.base.insect.exchange.message;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static net.talpidae.base.insect.exchange.message.Payload.extractString;


@Slf4j
@Builder
public class MappingPayload implements Payload
{
    public static final int MAXIMUM_SERIALIZED_SIZE = 780;

    public static final int TYPE_MAPPING = 0x1;

    @Getter
    private final int type;           // 0x1: mapping

    @Getter
    private final int flags;          // 0x0

    @Getter
    private final long timestamp;     // client System.nanoTime()

    @Getter
    private final int port;           // client port

    @Getter
    private final String host;        // client IPv4 address

    @Getter
    private final String route;       // route (exported path, zero-terminated UTF-8 string)

    @Getter
    private final String dependency;  // path (internal path, zero-terminated UTF-8 string)

    private final InetSocketAddress authorizedRemote = new InetSocketAddress(getHost(), getPort());

    protected static MappingPayload from(ByteBuffer buffer, int offset) throws IndexOutOfBoundsException
    {
        val type = buffer.get(offset) & 0xFF;
        if (type != TYPE_MAPPING)
        {
            return null;
        }

        val hostOffset = offset + 15;
        val hostLength = buffer.get(offset + 2) & 0xFF;        // length of host
        val routeOffset = hostOffset + hostLength;
        val routeLength = buffer.get(offset + 3) & 0xFF;       // length of route
        val dependencyOffset = routeOffset + routeLength;
        val dependencyLength = buffer.get(offset + 14) & 0xFF; // length of dependency

        return MappingPayload.builder()
                .type(buffer.get(offset) & 0xFF)
                .flags(buffer.get(offset + 1) & 0xFF)
                .timestamp(buffer.getLong(offset + 4))
                .port(buffer.getShort(offset + 12) & 0xFFFF)
                .host(extractString(buffer, hostOffset, hostLength))
                .route(extractString(buffer, routeOffset, routeLength))
                .dependency(extractString(buffer, dependencyOffset, dependencyLength))
                .build();
    }


    @Override
    public void to(ByteBuffer buffer, int offset)
    {

    }


    /**
     * Check subject address against sender address.
     */
    public boolean isAuthorative(InetSocketAddress remoteAddress)
    {
        return authorizedRemote.equals(remoteAddress);
    }

    @Override
    public int getMaximumSize()
    {
        return MAXIMUM_SERIALIZED_SIZE;
    }


    @Override
    public String toString()
    {
        return Integer.toHexString(getType()) + ", " +
                Integer.toHexString(flags) + ", " +
                getTimestamp() + ", " +
                getPort() + ", " +
                getRoute() + ", " +
                getDependency();
    }


    /**
     * Base class for lombok builder.
     */
    public static class MappingPayloadBuilder
    {
        private long timestamp = System.nanoTime();

        private int type = TYPE_MAPPING;

        private int flags = 0;

        private String dependency = "";
    }
}