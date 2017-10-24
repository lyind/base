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

import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import net.talpidae.base.event.ServerShutdown;
import net.talpidae.base.event.ServerStarted;
import net.talpidae.base.event.Shutdown;
import net.talpidae.base.insect.metrics.MetricsSink;
import net.talpidae.base.resource.JerseyApplication;
import net.talpidae.base.server.performance.MetricsHandler;
import net.talpidae.base.util.ssl.SslContextFactory;

import org.glassfish.jersey.servlet.ServletContainer;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.websocket.server.ServerEndpointConfig;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.websockets.jsr.DefaultContainerConfigurator;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static io.undertow.servlet.Servlets.deployment;


@Slf4j
@Singleton
public class UndertowServer implements Server
{
    private final byte[] LOCK = new byte[0];

    private final ServerConfig serverConfig;

    private final ClassIntrospecter classIntrospecter;

    private final Class<? extends WebSocketEndpoint> annotatedEndpointClass;

    private final ServerEndpointConfig programmaticEndpointConfig;

    private final EventBus eventBus;

    private final ServerEndpointConfig.Configurator defaultServerEndpointConfigurator;

    private final MetricsSink metricsSink;

    private Undertow server = null;

    private GracefulShutdownHandler rootHandler;


    @Inject
    public UndertowServer(EventBus eventBus,
                          ServerConfig serverConfig,
                          ClassIntrospecter classIntrospecter,
                          Optional<Class<? extends WebSocketEndpoint>> annotatedEndpointClass,
                          Optional<ServerEndpointConfig> programmaticEndpointConfig,
                          Optional<ServerEndpointConfig.Configurator> defaultServerEndpointConfigurator,
                          Optional<MetricsSink> metricsSink)
    {
        this.serverConfig = serverConfig;
        this.classIntrospecter = classIntrospecter;
        this.annotatedEndpointClass = annotatedEndpointClass.orElse(null);
        this.programmaticEndpointConfig = programmaticEndpointConfig.orElse(null);
        this.defaultServerEndpointConfigurator = defaultServerEndpointConfigurator.orElse(null);
        this.metricsSink = metricsSink.orElse(null);
        this.eventBus = eventBus;

        eventBus.register(this);
    }


    private HttpHandler enableJerseyApplication(Class<?> jerseyApplicationClass) throws ServletException
    {
        // build regular JAX-RS servlet and deploy
        return enableHttpServlet(
                Servlets.servlet("jerseyServlet", ServletContainer.class)
                        .setLoadOnStartup(1)
                        .addInitParam("javax.ws.rs.Application", jerseyApplicationClass.getName())
                        .addMapping("/*"),
                jerseyApplicationClass.getSimpleName() + ".war",
                jerseyApplicationClass.getClassLoader());
    }


    private HttpHandler enableCustomServlet(Class<? extends HttpServlet> customHttpServletClass) throws ServletException
    {
        try
        {
            return enableHttpServlet(
                    Servlets.servlet("customHttpServlet", customHttpServletClass, classIntrospecter.createInstanceFactory(customHttpServletClass))
                            .setLoadOnStartup(1)
                            .addMapping("/*"),
                    customHttpServletClass.getSimpleName() + ".war",
                    customHttpServletClass.getClassLoader());
        }
        catch (NoSuchMethodException e)
        {
            throw new ServletException("failed to enable custom servlet class: " + customHttpServletClass.getSimpleName(), e);
        }
    }


    private HttpHandler enableHttpServlet(ServletInfo servletInfo, String deploymentName, ClassLoader classLoader) throws ServletException
    {
        synchronized (LOCK)
        {
            if (server == null)
            {

                val servletDeployment = deployment()
                        .setClassIntrospecter(classIntrospecter)
                        .setContextPath("/")
                        .setDeploymentName(deploymentName)
                        .setClassLoader(classLoader)
                        .addServlets(servletInfo);

                // deploy servlet
                val servletManager = Servlets.defaultContainer().addDeployment(servletDeployment);
                servletManager.deploy();

                return servletManager.start();
            }
            else
            {
                throw new IllegalStateException("mustn't call enableHttpServlet() while server is running");
            }
        }
    }


    private HttpHandler enableAnnotatedWebSocketApplication(Class<? extends WebSocketEndpoint> endpointClass) throws ServletException
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
                            .setDeploymentName("websocket-annotated-deployment")
                            .setClassLoader(endpointClass.getClassLoader());

                    val websocketManager = Servlets.defaultContainer().addDeployment(websocketDeployment);
                    websocketManager.deploy();

                    return websocketManager.start();
                }
                catch (IOException e)
                {
                    throw new ServletException("failed to create Xnio worker for annotated websocket servlet", e);
                }
            }
            else
            {
                throw new IllegalStateException("mustn't call enableAnnotatedWebSocketApplication() while server is running");
            }
        }
    }


    private HttpHandler enableProgrammaticWebSocketApplication(ServerEndpointConfig endpointConfig) throws ServletException
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

                    // add configurator to allow for injection into the endpoint
                    if (endpointConfig.getConfigurator() instanceof DefaultContainerConfigurator)
                    {
                        endpointConfig = ServerEndpointConfig.Builder.create(endpointConfig.getEndpointClass(), endpointConfig.getPath())
                                .subprotocols(endpointConfig.getSubprotocols())
                                .configurator(defaultServerEndpointConfigurator)
                                .decoders(endpointConfig.getDecoders())
                                .encoders(endpointConfig.getEncoders())
                                .extensions(endpointConfig.getExtensions())
                                .build();
                    }

                    // build websocket servlet
                    val webSocketDeploymentInfo = new WebSocketDeploymentInfo().addEndpoint(endpointConfig).setWorker(worker).setBuffers(buffers);

                    val websocketDeployment = deployment()
                            .setClassIntrospecter(classIntrospecter)
                            .setContextPath("/")
                            .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, webSocketDeploymentInfo)
                            .setDeploymentName("websocket-programmatic-deployment")
                            .setClassLoader(endpointConfig.getClass().getClassLoader());

                    val websocketManager = Servlets.defaultContainer().addDeployment(websocketDeployment);
                    websocketManager.deploy();

                    return websocketManager.start();
                }
                catch (IOException e)
                {
                    throw new ServletException("failed to create Xnio worker for programmatic websocket servlet", e);
                }
            }
            else
            {
                throw new IllegalStateException("mustn't call enableProgrammaticWebSocketApplication() while server is running");
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
                rootHandler.shutdown();
                rootHandler.addShutdownListener(new ServerShutdownListener());
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
        if (!serverConfig.isDisableHttp2())
        {
            builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
        }

        if (Strings.isNullOrEmpty(serverConfig.getKeyStorePath()))
        {
            builder.addHttpListener(serverConfig.getPort(), serverConfig.getHost());
        }
        else
        {
            val sslContextFactory = new SslContextFactory(serverConfig);
            try
            {
                builder.addHttpsListener(serverConfig.getPort(), serverConfig.getHost(), sslContextFactory.createSslContext());
            }
            catch (IOException e)
            {
                log.error("Failed to create SSL context: {}", e.getMessage(), e);
            }
        }

        val haveJerseyResources = serverConfig.getJerseyResourcePackages() != null && serverConfig.getJerseyResourcePackages().length > 0;
        val customHttpServletClass = serverConfig.getCustomHttpServletClass();

        // enable features as defined by serverConfig
        HttpHandler servletHandler = null;
        if (haveJerseyResources)
        {
            servletHandler = enableJerseyApplication(JerseyApplication.class);
        }

        if (customHttpServletClass != null)
        {
            val customServletHandler = enableCustomServlet(customHttpServletClass);
            if (servletHandler == null)
            {
                servletHandler = customServletHandler;
            }
        }

        if (annotatedEndpointClass != null)
        {
            val webSocketHandler = enableAnnotatedWebSocketApplication(annotatedEndpointClass);
            if (servletHandler == null)
            {
                servletHandler = webSocketHandler;
            }
        }
        else if (programmaticEndpointConfig != null)
        {
            val webSocketHandler = enableProgrammaticWebSocketApplication(programmaticEndpointConfig);
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
            // enhance handler with X-Forwarded-* support
            rootHandler = Handlers.proxyPeerAddress(rootHandler);
        }

        if (serverConfig.isLoggingFeatureEnabled())
        {
            // enable extensive logging (make sure to disable for production)
            rootHandler = Handlers.requestDump(rootHandler);
        }

        if (metricsSink != null)
        {
            // enable metrics
            rootHandler = new MetricsHandler(rootHandler, metricsSink);
        }

        // finally, enhance handler with graceful shutdown capability
        builder.setHandler(this.rootHandler = Handlers.gracefulShutdown(rootHandler));
    }

    @Override
    public void start() throws ServletException
    {
        final boolean isJustStarted;
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

                isJustStarted = true;
            }
            else
            {
                isJustStarted = false;
            }
        }

        if (isJustStarted)
        {
            eventBus.post(new ServerStarted());
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

            eventBus.post(new ServerShutdown());
        }
    }
}
