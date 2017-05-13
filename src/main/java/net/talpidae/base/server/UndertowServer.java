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
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.ProxyPeerAddressHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.event.Shutdown;
import net.talpidae.base.resource.JerseyApplication;
import org.glassfish.jersey.servlet.ServletContainer;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.InetSocketAddress;

import static io.undertow.servlet.Servlets.deployment;


@Slf4j
@Singleton
public class UndertowServer implements Server
{
    private final byte[] LOCK = new byte[0];

    private final ServerConfig serverConfig;

    private final ServerShutdownListener shutdownListener = new ServerShutdownListener();

    private final ClassIntrospecter classIntrospecter;

    private final Class<? extends WebSocketEndpoint> webSocketEndPoint;

    private Undertow server = null;

    private GracefulShutdownHandler rootHandler;


    @Inject
    public UndertowServer(EventBus eventBus, ServerConfig serverConfig, ClassIntrospecter classIntrospecter, Class<? extends WebSocketEndpoint> webSocketEndpoint)
    {
        this.serverConfig = serverConfig;
        this.classIntrospecter = classIntrospecter;
        this.webSocketEndPoint = webSocketEndpoint;

        eventBus.register(this);
    }

    private static ProxyPeerAddressHandler attachProxyPeerAddressHandler(HttpHandler handler)
    {
        final ProxyPeerAddressHandler proxyPeerAddressHandler;
        if (handler instanceof ProxyPeerAddressHandler)
        {
            proxyPeerAddressHandler = (ProxyPeerAddressHandler) handler;
        }
        else
        {
            // enhance handler with X-Forwarded-* support
            proxyPeerAddressHandler = Handlers.proxyPeerAddress(handler);
        }

        return proxyPeerAddressHandler;
    }

    private static GracefulShutdownHandler attachGracefulShutdownHandler(HttpHandler handler)
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

        return gracefulShutdownHandler;
    }

    private HttpHandler enableJerseyApplication(Class<?> jerseyApplicationClass) throws ServletException
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

                //addHandler(Handlers.path(Handlers.redirect("/")).addPrefixPath("/", servletManager.start()));
                return servletManager.start();
            }
            else
            {
                throw new IllegalStateException("mustn't call enableJerseyApplication() while server is running");
            }
        }
    }

    private HttpHandler enableWebSocketApplication(Class<? extends WebSocketEndpoint> endpointClass) throws ServletException
    {
        synchronized (LOCK)
        {
            if (server == null)
            {
                // configure worker/NIO
                val nio = Xnio.getInstance("nio", Undertow.class.getClassLoader());
                try
                {
                    val worker = nio.createWorker(OptionMap.builder().getMap());
                    val buffers = new DefaultByteBufferPool(true, 1024 * 16, -1, 4);

                    // build websocket servlet
                    val webSocketDeploymentInfo = new WebSocketDeploymentInfo().addEndpoint(endpointClass).setWorker(worker).setBuffers(buffers);

                    val websocketDeployment = deployment()
                            .setClassIntrospecter(classIntrospecter)
                            .setContextPath("/")
                            .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, webSocketDeploymentInfo)
                            .setDeploymentName("websocket-deployment")
                            .setClassLoader(endpointClass.getClassLoader());

                    val websocketManager = Servlets.defaultContainer().addDeployment(websocketDeployment);
                    websocketManager.deploy();

                    //addHandler(Handlers.path(Handlers.redirect("/")).addPrefixPath("/", websocketManager.start()));
                    return websocketManager.start();
                }
                catch (IOException e)
                {
                    throw new ServletException("failed to create Xnio worker for websocket servlet", e);
                }
            }
            else
            {
                throw new IllegalStateException("mustn't call enableWebSocketApplication() while server is running");
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

                // put handlers into shutdown mode
                rootHandler.addShutdownListener(shutdownListener);
                rootHandler.shutdown();
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
                LOCK.wait();
            }
        }
        catch (InterruptedException e)
        {
            // wait again, need to shutdown server
            log.info("waitForShutdown(): interrupted, stopping server");
            shutdown();
        }
    }

    private void configureServer(Undertow.Builder builder) throws ServletException
    {
        builder.addHttpListener(serverConfig.getPort(), serverConfig.getHost());

        // enable features as defined by serverConfig
        HttpHandler servletHandler = null;
        if (serverConfig.getJerseyResourcePackages() != null && serverConfig.getJerseyResourcePackages().length > 0)
        {
            servletHandler = enableJerseyApplication(JerseyApplication.class);
        }

        if (!webSocketEndPoint.isAssignableFrom(DisabledWebSocketEndpoint.class))
        {
            val webSocketHandler = enableWebSocketApplication(webSocketEndPoint);
            if (servletHandler == null)
            {
                servletHandler = webSocketHandler;
            }
        }

        HttpHandler rootHandler = ResponseCodeHandler.HANDLE_404;
        if (servletHandler != null)
        {
            rootHandler = servletHandler;
        }

        val rootHandlerWrapper = serverConfig.getRootHandlerWrapper();
        if (rootHandlerWrapper != null)
        {
            rootHandler = rootHandlerWrapper.wrap(rootHandler);
        }

        if (serverConfig.isBehindProxy())
        {
            rootHandler = attachProxyPeerAddressHandler(rootHandler);
        }

        builder.setHandler(this.rootHandler = attachGracefulShutdownHandler(rootHandler));
    }

    @Override
    public void start() throws ServletException
    {
        synchronized (LOCK)
        {
            if (server == null)
            {
                val builder = Undertow.builder();

                configureServer(builder);

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
                    log.info("HTTP handler shutdown, stopping server");
                }
                else
                {
                    log.info("HTTP handler shutdown failed, stopping server interrupting clients");
                }

                LOCK.notifyAll();

                server.stop();
                server = null;
            }
        }
    }
}
