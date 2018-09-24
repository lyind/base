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

package net.talpidae.base.client;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.plugins.interceptors.AcceptEncodingGZIPFilter;
import org.jboss.resteasy.plugins.interceptors.ClientContentEncodingAnnotationFeature;
import org.jboss.resteasy.plugins.interceptors.GZIPDecodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.GZIPEncodingInterceptor;
import org.jboss.resteasy.plugins.providers.ByteArrayProvider;
import org.jboss.resteasy.plugins.providers.DefaultBooleanWriter;
import org.jboss.resteasy.plugins.providers.DefaultNumberWriter;
import org.jboss.resteasy.plugins.providers.DefaultTextPlain;
import org.jboss.resteasy.plugins.providers.FileProvider;
import org.jboss.resteasy.plugins.providers.FileRangeWriter;
import org.jboss.resteasy.plugins.providers.InputStreamProvider;
import org.jboss.resteasy.plugins.providers.StringTextStar;


public class ClientModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        // default interceptors
        bind(AcceptEncodingGZIPFilter.class);
        bind(ClientContentEncodingAnnotationFeature.class);
        bind(GZIPEncodingInterceptor.class);
        //bind(ServerContentEncodingAnnotationFilter.class);
        bind(GZIPDecodingInterceptor.class);

        // default providers
        bind(InputStreamProvider.class);
        bind(ByteArrayProvider.class);
        bind(DefaultBooleanWriter.class);
        bind(DefaultNumberWriter.class);
        bind(DefaultTextPlain.class);
        bind(FileProvider.class);
        bind(FileRangeWriter.class);
        bind(StringTextStar.class);

        bind(JacksonProvider.class);
        bind(ObjectMapperProvider.class);
        bind(AuthenticationInheritanceRequestFilter.class);
        bind(AuthScopeTokenForwardRequestFilter.class);
        bind(InsectNameUserAgentRequestFilter.class);
        bind(LoadBalancingRequestFilter.class);
        bind(LoadBalancingWebTargetFactory.class);

        OptionalBinder.newOptionalBinder(binder(), ClientConfiguration.class).setDefault().to(DefaultClientConfig.class);
    }
}
