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

package net.talpidae.base.server;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;
import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.websockets.jsr.DefaultContainerConfigurator;

import javax.annotation.Nullable;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Optional;


public class ServerModule extends AbstractModule
{

    @Override
    protected void configure()
    {
        // use undertow server by default
        OptionalBinder.newOptionalBinder(binder(), ServerConfig.class).setDefault().to(DefaultServerConfig.class);
        OptionalBinder.newOptionalBinder(binder(), Server.class).setDefault().to(UndertowServer.class);

        // websocket support
        OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<Class<? extends WebSocketEndpoint>>() {});
        OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<ServerEndpointConfig>() {});

        bind(ClassIntrospecter.class).to(GuiceClassIntrospecter.class);
    }


    @Provides
    @Nullable
    @Singleton
    public Optional<ServerEndpointConfig.Configurator> provideDefaultServerEndpointConfigurator(Optional<ServerEndpointConfig> serverEndpointConfig, Injector injector)
    {
        return serverEndpointConfig.map(endpointConfig -> new GuiceServerEndpointConfigurator(injector));
    }


    /**
     * Work around the 0-argument constructor limitation when creating annotated WebSocket servlet deployments.
     */
    private static class GuiceClassIntrospecter implements ClassIntrospecter
    {
        private final Injector injector;


        @Inject
        public GuiceClassIntrospecter(Injector injector)
        {
            this.injector = injector;
        }

        @Override
        public <T> InstanceFactory<T> createInstanceFactory(Class<T> clazz) throws NoSuchMethodException
        {
            return new ImmediateInstanceFactory<>(injector.getInstance(clazz));
        }
    }


    /**
     * Work around the 0-argument constructor limitation when creating programmatic WebSocket servlet deployments.
     */
    private static class GuiceServerEndpointConfigurator extends DefaultContainerConfigurator
    {
        private final Injector injector;


        GuiceServerEndpointConfigurator(Injector injector)
        {
            this.injector = injector;
        }


        @Override
        public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException
        {
            return injector.getInstance(endpointClass);
        }
    }
}
