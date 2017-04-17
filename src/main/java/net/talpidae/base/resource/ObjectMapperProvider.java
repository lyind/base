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

package net.talpidae.base.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;


/**
 * Special "Jersey Provider" to force that configured ObjectMapper down Jersey's throat.
 *
 * Obsolete as soon as we find a more reliable way to make Jersey use our Guice bindings.
 */
@Singleton
@Provider
public class ObjectMapperProvider implements ContextResolver<ObjectMapper>
{
    private final ObjectMapper mapper;

    @Inject
    public ObjectMapperProvider(ObjectMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public ObjectMapper getContext(Class<?> type)
    {
        return mapper;
    }
}
