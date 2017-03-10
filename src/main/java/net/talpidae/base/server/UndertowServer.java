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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.servlet.Servlets;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.event.Shutdown;
import net.talpidae.base.resource.JerseyApplication;
import net.talpidae.base.resource.WebSocketEndpoint;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import static io.undertow.servlet.Servlets.deployment;


@Slf4j
@Singleton
public class UndertowServer implements Server
{
    private final byte[] LOCK = new byte[0];

    private final Set<GracefulShutdownHandler> handlers = new HashSet<>();

    private final ServerConfig serverConfig;

    private final ServerShutdownListener shutdownListener = new ServerShutdownListener();

    private volatile int handlersStarted = 0;

    private Undertow server = null;


    @Inject
    public UndertowServer(EventBus eventBus, ServerConfig serverConfig)
    {
        eventBus.register(this);

        this.serverConfig = serverConfig;
    }

    private void enableJerseyApplication(Class<?> jerseyApplicationClass) throws ServletException
    {
        synchronized (LOCK)
        {
            if (server == null)
            {
                // build regular JAX-RS servlet
                val servletDeployment = deployment()
                        .setClassLoader(jerseyApplicationClass.getClassLoader())
                        .setContextPath("/")
                        .setDeploymentName(jerseyApplicationClass.getSimpleName() + ".war")
                        //.addListeners(listener(Listener.class))
                        .setClassLoader(jerseyApplicationClass.getClassLoader())
                        .addServlets(Servlets.servlet("jerseyServlet", ServletContainer.class)
                                .setLoadOnStartup(1)
                                .addInitParam("javax.ws.rs.Application", jerseyApplicationClass.getName())
                                .addMapping("/*"));

                // deploy servlet
                val servletManager = Servlets.defaultContainer().addDeployment(servletDeployment);
                servletManager.deploy();

                addHandler(Handlers.path(Handlers.redirect("/")).addPrefixPath("/", servletManager.start()));
            }
            else
            {
                throw new IllegalStateException("mustn't call enableJerseyApplication() while server is running");
            }
        }
    }

    private void enableWebSocketApplication(Class<?> endpointClass) throws ServletException
    {
        synchronized (LOCK)
        {
            if (server == null)
            {
                // build websocket servlet
                val webSocketDeploymentInfo = new WebSocketDeploymentInfo().addEndpoint(endpointClass);

                val websocketDeployment = deployment()
                        .setContextPath("/")
                        .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, webSocketDeploymentInfo)
                        .setDeploymentName("websocket-deployment")
                        .setClassLoader(endpointClass.getClassLoader());

                val websocketManager = Servlets.defaultContainer().addDeployment(websocketDeployment);
                websocketManager.deploy();

                addHandler(Handlers.path(Handlers.redirect("/")).addPrefixPath("/", websocketManager.start()));
            }
            else
            {
                throw new IllegalStateException("mustn't call enableWebSocketApplication() while server is running");
            }
        }
    }

    private void addHandler(HttpHandler handler)
    {
        synchronized (LOCK)
        {
            if (server == null)
            {
                final GracefulShutdownHandler gracefulShutdownHandler;
                if (handler instanceof GracefulShutdownHandler)
                {
                    gracefulShutdownHandler = (GracefulShutdownHandler) handler;
                }
                else
                {
                    // enhance handler with graceful shutdown capability
                    gracefulShutdownHandler = Handlers.gracefulShutdown(handler);
                }

                handlers.add(gracefulShutdownHandler);
            }
            else
            {
                throw new IllegalStateException("mustn't call addHandler() while server is running");
            }
        }
    }

    @Subscribe
    public void shutdownEvent(Shutdown event)
    {
        shutdown();
    }

    private void shutdown()
    {
        synchronized (LOCK)
        {
            if (server != null)
            {
                log.info("server shutdown requested");

                // put all handlers into shutdown mode
                for (val handler : handlers)
                {
                    handler.shutdown();
                    handler.addShutdownListener(shutdownListener);
                }

                server.stop();

                handlers.clear();
                server = null;
            }
        }
    }

    @Override
    public void waitForShutdown()
    {
        try
        {
            synchronized (LOCK)
            {
                while (handlersStarted > 0)
                {
                    LOCK.wait();
                }
            }
        }
        catch (InterruptedException e)
        {
            // wait again, need to shutdown server
            log.info("waitForShutdown(): interrupted, stopping server");
            shutdown();
        }
    }

    private void configureServer() throws ServletException
    {
        // enable features as defined by serverConfig
        if (serverConfig.getJerseyResourcePackages() != null && serverConfig.getJerseyResourcePackages().length > 0)
        {
            enableJerseyApplication(JerseyApplication.class);
        }

        if (serverConfig.getWebSocketHandler() != null)
        {
            enableWebSocketApplication(WebSocketEndpoint.class);
        }

        if (serverConfig.getAdditionalHandlers() != null && !serverConfig.getAdditionalHandlers().isEmpty())
        {
            for (HttpHandler handler : serverConfig.getAdditionalHandlers())
            {
                addHandler(handler);
            }
        }
    }

    @Override
    public void start() throws ServletException
    {
        synchronized (LOCK)
        {
            if (server == null)
            {
                val builder = Undertow.builder();

                builder.addHttpListener(0, serverConfig.getHost());

                configureServer();

                // register all handlers
                handlersStarted = handlers.size();
                for (val handler : handlers)
                {
                    builder.setHandler(handler);
                }

                server = builder.build();
                server.start();

                for (val info : server.getListenerInfo())
                {
                    if (info.getAddress() instanceof InetSocketAddress)
                    {
                        serverConfig.setPort(((InetSocketAddress) info.getAddress()).getPort());
                        serverConfig.setHost(((InetSocketAddress) info.getAddress()).getHostString());
                    }
                }
            }
        }
    }


    private class ServerShutdownListener implements GracefulShutdownHandler.ShutdownListener
    {
        @Override
        public void shutdown(boolean shutdownSuccessful)
        {
            synchronized (LOCK)
            {
                if (shutdownSuccessful)
                {
                    --handlersStarted;
                }

                if (handlersStarted <= 0)
                {
                    log.info("all HTTP handlers shutdown");
                    LOCK.notifyAll();
                }
            }
        }
    }
}
