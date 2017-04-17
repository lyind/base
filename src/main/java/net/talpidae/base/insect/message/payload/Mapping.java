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

package net.talpidae.base.insect.message.payload;

import com.google.common.base.Strings;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

import static net.talpidae.base.insect.message.payload.Payload.extractString;
import static net.talpidae.base.insect.message.payload.Payload.toTruncatedUTF8;


@Slf4j
@Builder
public class Mapping implements Payload
{
    public static final int MAXIMUM_SERIALIZED_SIZE = 1036;

    public static final int TYPE_MAPPING = 0x1;

    private static final int STRING_SIZE_MAX = 255;

    @Getter
    @Builder.Default
    private final int type = TYPE_MAPPING;             // 0x1: mapping

    @Builder.Default
    @Getter
    private final int flags = 0;                       // 0x0

    @Builder.Default
    @Getter
    private final long timestamp = System.nanoTime();  // client System.nanoTime()

    @Getter
    private final int port;                // client port

    @Getter
    private final String host;             // client IPv4 address

    @Getter
    private final String route;            // route

    @Builder.Default
    @Getter
    private final String name;             // name (unique service instance ID)

    @Builder.Default
    @Getter
    private final String dependency = "";  // empty or one of the services dependencies

    @Getter
    @NonNull
    private final InetSocketAddress socketAddress;


    static Mapping from(ByteBuffer buffer, int offset) throws IndexOutOfBoundsException
    {
        val type = buffer.get(offset) & 0xFF;
        if (type != TYPE_MAPPING)
        {
            return null;
        }

        val hostOffset = offset + 16;
        val hostLength = buffer.get(offset + 2) & 0xFF;        // length of host
        val routeOffset = hostOffset + hostLength;
        val routeLength = buffer.get(offset + 3) & 0xFF;       // length of route
        val port = buffer.getShort(offset + 12) & 0xFFFF;
        val nameOffset = routeOffset + routeLength;
        val nameLength = buffer.get(offset + 14) & 0xFF;       // length of name
        val dependencyOffset = nameOffset + nameLength;
        val dependencyLength = buffer.get(offset + 15) & 0xFF; // length of dependency

        val host = extractString(buffer, hostOffset, hostLength);
        return Mapping.builder()
                .type(type)
                .flags(buffer.get(offset + 1) & 0xFF)
                .timestamp(buffer.getLong(offset + 4))
                .port(port)
                .host(host)
                .route(extractString(buffer, routeOffset, routeLength))
                .name(extractString(buffer, nameOffset, nameLength))
                .dependency(extractString(buffer, dependencyOffset, dependencyLength))
                .socketAddress(InetSocketAddress.createUnresolved(host, port))
                .build();
    }


    @Override
    public void to(ByteBuffer buffer)
    {
        val hostBytes = toTruncatedUTF8(host, STRING_SIZE_MAX);
        val routeBytes = toTruncatedUTF8(route, STRING_SIZE_MAX);
        val nameBytes = toTruncatedUTF8(name, STRING_SIZE_MAX);
        val dependencyBytes = toTruncatedUTF8(dependency, STRING_SIZE_MAX);

        buffer.put((byte) type);
        buffer.put((byte) flags);
        buffer.put((byte) hostBytes.length);
        buffer.put((byte) routeBytes.length);
        buffer.putLong(timestamp);
        buffer.putShort((short) port);
        buffer.put((byte) nameBytes.length);
        buffer.put((byte) dependencyBytes.length);
        buffer.put(hostBytes);
        buffer.put(routeBytes);
        buffer.put(nameBytes);
        buffer.put(dependencyBytes);
    }


    /**
     * Check subject address against sender address.
     */
    public boolean isAuthorative(InetSocketAddress remoteAddress)
    {
        val authorizedHostOrAddress = getSocketAddress().getHostString();
        val authorizedPort = getSocketAddress().getPort();

        return authorizedPort == remoteAddress.getPort()
                && (authorizedHostOrAddress.equals(remoteAddress.getHostString())
                || authorizedHostOrAddress.equals(remoteAddress.getAddress().getHostAddress()));
    }

    @Override
    public int getMaximumSize()
    {
        return MAXIMUM_SERIALIZED_SIZE;
    }


    @Override
    public String toString()
    {
        val dependency = getDependency();
        return Integer.toHexString(getType()) + ", " +
                Integer.toHexString(flags) + ", " +
                getTimestamp() + ", " +
                getPort() + ", " +
                getRoute() + ", " +
                getName() +
                ((Strings.isNullOrEmpty(dependency)) ? "" : ", " + dependency);
    }
}