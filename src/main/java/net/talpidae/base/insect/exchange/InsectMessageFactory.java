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

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.PooledSoftReference;

import java.lang.ref.SoftReference;


public class InsectMessageFactory implements PooledObjectFactory<InsectMessage>
{
    @Override
    public PooledObject<InsectMessage> makeObject() throws Exception
    {
        return new PooledSoftReference<>(new SoftReference<>(new InsectMessage()));
    }

    @Override
    public void destroyObject(PooledObject<InsectMessage> p) throws Exception
    {
        // nothing to do
    }

    @Override
    public boolean validateObject(PooledObject<InsectMessage> p)
    {
        return true;
    }

    @Override
    public void activateObject(PooledObject<InsectMessage> p) throws Exception
    {
        // nothing to do
    }

    @Override
    public void passivateObject(PooledObject<InsectMessage> p) throws Exception
    {
        p.getObject().clear();
    }
}
