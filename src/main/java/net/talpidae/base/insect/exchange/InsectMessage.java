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

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.exchange.message.MappingPayload;
import net.talpidae.base.insect.exchange.message.Payload;
import net.talpidae.base.insect.exchange.message.PayloadFactory;

import java.net.InetSocketAddress;


@Slf4j
public class InsectMessage extends BaseMessage
{
    private Payload payload;


    public InsectMessage()
    {
        super(MappingPayload.MAXIMUM_SERIALIZED_SIZE);
    }


    public Payload getPayload(boolean enforceAuthority) throws IndexOutOfBoundsException
    {
        if (payload == null)
        {
            val newMapping = PayloadFactory.unpackPayload(getBuffer(), 0);
            if (newMapping != null)
            {
                // validate client address (avoid message spoofing)
                if (!enforceAuthority || newMapping.isAuthorative(getRemoteAddress()))
                {
                    payload = newMapping;
                }
                else
                {
                    val remote = getRemoteAddress();
                    log.warn("possible spoofing attempt: remote host \"{}\" not authorized to send this message: {}",
                            (remote != null) ? remote.getHostString() : "",
                            newMapping);
                }
            }
        }

        return payload;
    }


    public void setPayload(Payload newPayload, InetSocketAddress socketAddress)
    {
        if (payload != null)
        {
            throw new IllegalStateException("payload has already been set");
        }

        setRemoteAddress(socketAddress); // may be different from

        payload = newPayload;
        payload.to(getBuffer(), 0);
    }


    @Override
    public void clear()
    {
        payload = null;

        super.clear();
    }
}
