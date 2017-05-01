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

package net.talpidae.base.util.network;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.talpidae.base.util.collection.Enumeration.asStream;


@Singleton
@Slf4j
public class NetworkUtil
{
    private static final long CACHED_LOCAL_ADDRESSES_LIFETIME = TimeUnit.SECONDS.toNanos(45);

    private final AtomicReference<Set<InterfaceAddress>> cachedLocalAddresses = new AtomicReference<>(Collections.emptySet());

    private volatile long cachedLocalAddressesTimeoutMs = 0;


    /**
     * Get a set of all local interface addresses.
     */
    public Set<InterfaceAddress> getInterfaceAddresses()
    {
        val cachedAddresses = cachedLocalAddresses.get();
        val now = System.nanoTime();
        if (now > cachedLocalAddressesTimeoutMs || cachedAddresses.isEmpty())
        {
            val addresses = enumerateLocalAddresses();
            if (cachedLocalAddresses.compareAndSet(cachedAddresses, addresses))
            {
                cachedLocalAddressesTimeoutMs = now + CACHED_LOCAL_ADDRESSES_LIFETIME;
                return addresses;
            }

            return Collections.unmodifiableSet(cachedLocalAddresses.get());
        }

        return Collections.unmodifiableSet(cachedAddresses);
    }


    /**
     * In case of any-local or wildcard localAddress, try to determine a local address that remoteAddress can reach.
     * <p>
     * TODO Maybe improve this (mainly for the "via default gateway" case): https://stackoverflow.com/questions/11797641/java-finding-network-interface-for-default-gateway
     */
    public InetAddress getReachableLocalAddress(InetAddress localAddress, InetAddress remoteAddress)
    {
        if (localAddress.isAnyLocalAddress())
        {
            InetAddress candidateAddress = null;
            for (val interfaceAddress : getInterfaceAddresses())
            {
                val ifaceAddress = interfaceAddress.getAddress();
                if (remoteAddress != null && isInSameNetwork(interfaceAddress, remoteAddress))
                {
                    return ifaceAddress;
                }
                else if (candidateAddress == null
                        && !ifaceAddress.isLoopbackAddress() && !ifaceAddress.isMulticastAddress() && !ifaceAddress.isLinkLocalAddress())
                {
                    candidateAddress = ifaceAddress;
                }
            }

            return candidateAddress;
        }

        return localAddress;
    }


    /**
     * Check if an address shares the network with an interface address.
     */
    private static boolean isInSameNetwork(InterfaceAddress interfaceAddress, InetAddress address)
    {
        val prefixLength = interfaceAddress.getNetworkPrefixLength();
        val ifaceAddress = interfaceAddress.getAddress();

        if (ifaceAddress.getClass().isAssignableFrom(address.getClass()))
        {
            return Arrays.equals(getNetworkPrefix(interfaceAddress.getAddress(), prefixLength), getNetworkPrefix(address, prefixLength));
        }

        // IPv6 != IPv4, let's assume they are not in the same network
        return false;
    }


    /**
     * Get big-endian network prefix of a certain length from address. The remaining bits are zero.
     */
    public static byte[] getNetworkPrefix(InetAddress address, int prefixLengthBits)
    {
        val addressBytes = address.getAddress();
        val prefixByteCount = (prefixLengthBits + 7) / 8;
        if (prefixByteCount > addressBytes.length)
        {
            throw new IllegalArgumentException("illegal prefix length specified for address of " + addressBytes.length + " bytes length: " + prefixLengthBits);
        }

        // copy all bytes
        val prefix = new byte[prefixByteCount];
        if (prefixLengthBits > 0)
        {
            val wholeByteCount = prefixLengthBits / 8;
            System.arraycopy(addressBytes, 0, prefix, 0, wholeByteCount);

            // final, partial byte
            val remainingBits = prefixLengthBits - (wholeByteCount * 8);
            if (remainingBits > 0)
            {
                prefix[wholeByteCount] |= ((addressBytes[wholeByteCount] & (0x0000FF00 >>> remainingBits)) & 0xFF);
            }
        }

        return prefix;
    }


    private static Set<InterfaceAddress> enumerateLocalAddresses()
    {
        try
        {
            return asStream(NetworkInterface.getNetworkInterfaces())
                    .filter(interfaceIsUp)
                    .map(NetworkInterface::getInterfaceAddresses)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
        }
        catch (SocketException e)
        {
            log.warn("failed to enumerate interfaces: {}", e.getMessage());
        }

        return Collections.emptySet();
    }


    private static final Predicate<NetworkInterface> interfaceIsUp = networkInterface ->
    {
        try
        {
            return networkInterface.isUp();
        }
        catch (SocketException e)
        {
            log.warn("failed to determine status of interface {}: {}", networkInterface.getName(), e.getMessage());
        }

        return false;
    };
}
