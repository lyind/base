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
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.server.session.SessionManagerStatistics;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.SessionManagerFactory;
import io.undertow.websockets.jsr.DefaultContainerConfigurator;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.event.ServerShutdown;
import net.talpidae.base.event.ServerStarted;
import net.talpidae.base.event.Shutdown;
import net.talpidae.base.insect.metrics.MetricsSink;
import net.talpidae.base.server.cors.CORSFilter;
import net.talpidae.base.server.performance.MemoryMetricCollector;
import net.talpidae.base.server.performance.MetricsHandler;
import net.talpidae.base.util.ssl.SslContextFactory;
import net.talpidae.base.util.thread.GeneralScheduler;
import org.xnio.OptionMap;
import org.xnio.Xnio;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.undertow.servlet.Servlets.deployment;


@Slf4j
@Singleton
public class UndertowServer implements Server
{
    private static final long MEMORY_METRICS_INTERVAL_SECONDS = 10;

    private final byte[] LOCK = new byte[0];

    private final ServerConfig serverConfig;

    private final ClassIntrospecter classIntrospecter;

    private final Class<? extends WebSocketEndpoint> annotatedEndpointClass;

    private final ServerEndpointConfig programmaticEndpointConfig;

    private final EventBus eventBus;

    private final ServerEndpointConfig.Configurator defaultServerEndpointConfigurator;

    private final MetricsSink metricsSink;

    private final GeneralScheduler scheduler;

    private Undertow server = null;

    private GracefulShutdownHandler rootHandler;


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Inject
    public UndertowServer(EventBus eventBus,
                          ServerConfig serverConfig,
                          ClassIntrospecter classIntrospecter,
                          Optional<Class<? extends WebSocketEndpoint>> annotatedEndpointClass,
                          Optional<ServerEndpointConfig> programmaticEndpointConfig,
                          Optional<ServerEndpointConfig.Configurator> defaultServerEndpointConfigurator,
                          Optional<MetricsSink> metricsSink,
                          GeneralScheduler scheduler)
    {
        this.serverConfig = serverConfig;
        this.classIntrospecter = classIntrospecter;
        this.annotatedEndpointClass = annotatedEndpointClass.orElse(null);
        this.programmaticEndpointConfig = programmaticEndpointConfig.orElse(null);
        this.defaultServerEndpointConfigurator = defaultServerEndpointConfigurator.orElse(null);
        this.metricsSink = metricsSink.orElse(null);
        this.eventBus = eventBus;
        this.scheduler = scheduler;

        eventBus.register(this);
    }

    private HttpHandler enableRestApplication() throws ServletException
    {
        // build regular JAX-RS servlet and deploy
        return enableHttpServlet(null,
                "rest-deployment.war",
                RestBootstrapServletExtension.class.getClassLoader(),
                new RestBootstrapServletExtension());
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
        return enableHttpServlet(servletInfo, deploymentName, classLoader, null);
    }

    private HttpHandler enableHttpServlet(ServletInfo servletInfo, String deploymentName, ClassLoader classLoader, ServletExtension servletExtension) throws ServletException
    {
        val deployment = deployment()
                .setClassIntrospecter(classIntrospecter)
                .setContextPath("/")
                .setSecurityDisabled(true)
                .setAuthorizationManager(null)
                .setSessionManagerFactory(NullSessionManagerFactory.INSTANCE)
                .setDeploymentName(deploymentName)
                .setClassLoader(classLoader);

        if (servletInfo != null)
        {
            deployment.addServlets(servletInfo);
        }

        if (servletExtension != null)
        {
            deployment.addServletExtension(servletExtension);
        }

        // deploy servlet
        val servletManager = Servlets.defaultContainer().addDeployment(deployment);
        servletManager.deploy();

        return servletManager.start();
    }

    private HttpHandler enableAnnotatedWebSocketApplication(Class<? extends WebSocketEndpoint> endpointClass) throws ServletException
    {
        // configure worker/NIO
        val nio = Xnio.getInstance("nio", Undertow.class.getClassLoader());
        try
        {
            val worker = nio.createWorker(OptionMap.builder().getMap());
            val buffers = new DefaultByteBufferPool(true, 1024 * 16, -1, 4);

            // build websocket servlet
            val webSocketDeploymentInfo = new WebSocketDeploymentInfo()
                    .addEndpoint(endpointClass)
                    .setWorker(worker)
                    .setDispatchToWorkerThread(true)
                    .setBuffers(buffers);

            val websocketDeployment = deployment()
                    .setClassIntrospecter(classIntrospecter)
                    .setContextPath("/")
                    .setSecurityDisabled(true)
                    .setAuthorizationManager(null)
                    .setSessionManagerFactory(NullSessionManagerFactory.INSTANCE)
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

    private HttpHandler enableProgrammaticWebSocketApplication(ServerEndpointConfig endpointConfig) throws ServletException
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
            val webSocketDeploymentInfo = new WebSocketDeploymentInfo()
                    .addEndpoint(endpointConfig)
                    .setWorker(worker)
                    .setDispatchToWorkerThread(true)
                    .setBuffers(buffers);

            val websocketDeployment = deployment()
                    .setClassIntrospecter(classIntrospecter)
                    .setContextPath("/")
                    .setSecurityDisabled(true)
                    .setAuthorizationManager(null)
                    .setSessionManagerFactory(NullSessionManagerFactory.INSTANCE).addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME, webSocketDeploymentInfo)
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
            while (server != null)
            {
                synchronized (LOCK)
                {
                    LOCK.wait(TimeUnit.SECONDS.toMillis(8));
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

    private void configureServer(Undertow.Builder builder) throws ServletException
    {
        // some default settings
        builder.setServerOption(UndertowOptions.ENABLE_STATISTICS, false);
        builder.setServerOption(UndertowOptions.IDLE_TIMEOUT, serverConfig.getIdleTimeout());
        builder.setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, serverConfig.getNoRequestTimeout());

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

        val customHttpServletClass = serverConfig.getCustomHttpServletClass();

        // enable features as defined by serverConfig
        HttpHandler servletHandler = null;
        if (serverConfig.isRestEnabled())
        {
            servletHandler = enableRestApplication();
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

        if (serverConfig.getCorsOriginPattern() != null)
        {
            rootHandler = new CORSFilter(rootHandler, serverConfig);
        }

        if (metricsSink != null)
        {
            // enable metrics
            rootHandler = new MetricsHandler(rootHandler, metricsSink);

            // TODO Put this somewhere else.
            scheduler.scheduleWithFixedDelay(new MemoryMetricCollector(metricsSink), MEMORY_METRICS_INTERVAL_SECONDS, MEMORY_METRICS_INTERVAL_SECONDS, TimeUnit.SECONDS);
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


    /**
     * We don't need container-managed sessions, we try to be state-less.
     */
    private static final class NullSessionManagerFactory implements SessionManagerFactory
    {
        public static final NullSessionManagerFactory INSTANCE = new NullSessionManagerFactory();

        @Override
        public SessionManager createSessionManager(Deployment deployment)
        {
            return NullSessionManager.INSTANCE;
        }

        private static final class NullSessionManager implements SessionManager
        {
            private static final SessionManager INSTANCE = new NullSessionManager();

            @Override
            public String getDeploymentName()
            {
                return NullSessionManager.class.getSimpleName();
            }

            @Override
            public void start()
            {
                // noop
            }

            @Override
            public void stop()
            {
                // noop
            }

            @Override
            public Session createSession(HttpServerExchange serverExchange, SessionConfig sessionCookieConfig)
            {
                return null;
            }

            @Override
            public Session getSession(HttpServerExchange serverExchange, SessionConfig sessionCookieConfig)
            {
                return null;
            }

            @Override
            public Session getSession(String sessionId)
            {
                return null;
            }

            @Override
            public void registerSessionListener(SessionListener listener)
            {
                // noop
            }

            @Override
            public void removeSessionListener(SessionListener listener)
            {
                // noop
            }

            @Override
            public void setDefaultSessionTimeout(int timeout)
            {
                // noop
            }

            @Override
            public Set<String> getTransientSessions()
            {
                return null;
            }

            @Override
            public Set<String> getActiveSessions()
            {
                return null;
            }

            @Override
            public Set<String> getAllSessions()
            {
                return null;
            }

            @Override
            public SessionManagerStatistics getStatistics()
            {
                return null;
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

            eventBus.post(new ServerShutdown());
        }
    }
}
