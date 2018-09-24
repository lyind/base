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

import com.google.inject.AbstractModule;

import net.talpidae.base.client.JacksonProvider;
import net.talpidae.base.client.ObjectMapperProvider;

import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.plugins.interceptors.AcceptEncodingGZIPFilter;
import org.jboss.resteasy.plugins.interceptors.CacheControlFeature;
import org.jboss.resteasy.plugins.interceptors.GZIPDecodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.GZIPEncodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.ServerContentEncodingAnnotationFeature;
import org.jboss.resteasy.plugins.providers.ByteArrayProvider;
import org.jboss.resteasy.plugins.providers.DefaultBooleanWriter;
import org.jboss.resteasy.plugins.providers.DefaultNumberWriter;
import org.jboss.resteasy.plugins.providers.DefaultTextPlain;
import org.jboss.resteasy.plugins.providers.FileProvider;
import org.jboss.resteasy.plugins.providers.FileRangeWriter;
import org.jboss.resteasy.plugins.providers.InputStreamProvider;
import org.jboss.resteasy.plugins.providers.StringTextStar;


public class RestModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        requireBinding(JacksonProvider.class);
        requireBinding(ObjectMapperProvider.class);
        requireBinding(ClientConfiguration.class);
        requireBinding(AcceptEncodingGZIPFilter.class);
        //bind(ClientContentEncodingAnnotationFilter.class);
        requireBinding(GZIPEncodingInterceptor.class);
        //bind(ServerContentEncodingAnnotationFilter.class);
        requireBinding(GZIPDecodingInterceptor.class);

        // default providers
        requireBinding(InputStreamProvider.class);
        requireBinding(ByteArrayProvider.class);
        requireBinding(DefaultBooleanWriter.class);
        requireBinding(DefaultNumberWriter.class);
        requireBinding(DefaultTextPlain.class);
        requireBinding(FileProvider.class);
        requireBinding(FileRangeWriter.class);
        requireBinding(StringTextStar.class);

        // server side default providers
        bind(CacheControlFeature.class);
        bind(ServerContentEncodingAnnotationFeature.class);

        bind(AuthBearerAuthenticationRequestFilter.class);
        bind(AuthenticationRequestFilter.class);
        bind(AuthRequiredRequestFilter.class);
        bind(BasicAuthAuthenticationFilter.class);
        bind(DefaultGenericExceptionMapper.class);
        bind(JsonMappingExceptionMapper.class);
        bind(DefaultRestApplication.class);
    }
}
