package net.talpidae.base.server;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import net.talpidae.base.resource.RestModule;
import net.talpidae.base.util.injector.JaxRsBindingInspector;

import org.jboss.resteasy.plugins.guice.GuiceResourceFactory;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.spi.ResteasyDeployment;

import java.util.ArrayList;
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

            val modules = Collections.singletonList(new RestModule());
            val childInjector = parentInjector != null
                    ? parentInjector.createChildInjector(modules)
                    : Guice.createInjector(modules);

            // inspired by GuiceResteasyBootstrapServletContextListener's ModuleProcessor
            // but doesn't register Resource interfaces
            val resources = new ArrayList<Binding<?>>();
            JaxRsBindingInspector.visitJaxRsBindings(childInjector,
                    (binding, type, targetType) -> {
                        // register resources after providers
                        log.debug("rest resource: {} bound to {}", type.getName(), targetType.getName());

                        resources.add(binding);
                    },
                    (binding, type) -> {
                        log.debug("rest provider: {}", type.getName());
                        providerFactory.registerProviderInstance(binding.getProvider().get());
                    }
            );

            for (val binding : resources)
            {
                val type = binding.getKey().getTypeLiteral().getType();
                val resourceFactory = new GuiceResourceFactory(binding.getProvider(), (Class) type);
                registry.addResourceFactory(resourceFactory);
            }
        }
    }


    @Override
    public void contextDestroyed(ServletContextEvent sce)
    {
        super.contextDestroyed(sce);
    }
}
