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

import com.google.inject.Injector;

import net.talpidae.base.util.injector.JaxRsBindingInspector;

import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Singleton
public class DefaultClientConfig extends ClientConfiguration
{
    @Inject
    public DefaultClientConfig(Injector injector, ResteasyProviderFactory resteasyProviderFactory)
    {
        super(resteasyProviderFactory);

        JaxRsBindingInspector.visitJaxRsBindings(injector,
                (binding, type, targetType) -> {
                    // no-op for the client
                },
                (binding, type) -> {
                    log.debug("client provider: {}", type.getName());
                    register(binding.getProvider().get());
                }
        );
    }
}