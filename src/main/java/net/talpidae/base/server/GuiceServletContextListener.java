package net.talpidae.base.server;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import net.talpidae.base.resource.RestModule;

import org.jboss.resteasy.plugins.guice.ModuleProcessor;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.spi.ResteasyDeployment;

import java.util.Collections;

import javax.servlet.ServletContextEvent;

import lombok.extern.slf4j.Slf4j;
import lombok.val;


/**
 * Simplified ServletContextListener that creates a Guice child injector and loads the RestModule
 * for each servlet that has a RESTEasy deployment.
 */
@Slf4j
public class GuiceServletContextListener extends ResteasyBootstrap
{
    private final Injector parentInjector;

    @Inject
    public GuiceServletContextListener(Injector parentInjector)
    {
        this.parentInjector = parentInjector;
    }

    @Override
    public void contextInitialized(final ServletContextEvent event)
    {
        super.contextInitialized(event);

        val ctx = event.getServletContext();
        val resteasyDeployment = (ResteasyDeployment) ctx.getAttribute(ResteasyDeployment.class.getName());
        if (resteasyDeployment != null)
        {
            log.debug("bootstrapping RestModule");

            val registry = resteasyDeployment.getRegistry();
            val providerFactory = resteasyDeployment.getProviderFactory();
            val processor = new ModuleProcessor(registry, providerFactory);

            val modules = Collections.singletonList(new RestModule());
            val childInjector = parentInjector != null
                    ? parentInjector.createChildInjector(modules)
                    : Guice.createInjector(modules);

            for (Injector injector = childInjector; injector != null; injector = injector.getParent())
            {
                processor.processInjector(injector);
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce)
    {
        super.contextDestroyed(sce);
    }
}
