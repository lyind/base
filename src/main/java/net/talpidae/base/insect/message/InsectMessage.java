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

package net.talpidae.base.insect.message;

import net.talpidae.base.insect.exchange.BaseMessage;
import net.talpidae.base.insect.message.payload.Payload;
import net.talpidae.base.insect.message.payload.PayloadFactory;

import java.nio.charset.CharacterCodingException;

import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
public class InsectMessage extends BaseMessage
{
    private Payload payload;


    public InsectMessage()
    {
        super(PayloadFactory.getMaximumSerializedSize());
    }


    public Payload getPayload() throws IndexOutOfBoundsException, CharacterCodingException
    {
        if (payload == null)
        {
            val newMapping = PayloadFactory.unpackPayload(getBuffer(), 0);
            if (newMapping != null)
            {
                payload = newMapping;
            }
        }

        return payload;
    }


    public void setPayload(Payload newPayload)
    {
        if (payload != null)
        {
            throw new IllegalStateException("payload has already been set");
        }

        payload = newPayload;
        payload.to(getBuffer());

        getBuffer().flip();
    }


    @Override
    protected void clear()
    {
        payload = null;
    }
}
