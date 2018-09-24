package net.talpidae.base.server;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import net.talpidae.base.resource.RestModule;

import org.jboss.resteasy.plugins.guice.GuiceResourceFactory;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.util.GetRestful;

import java.util.ArrayList;
import java.util.Collections;

import javax.annotation.Resource;
import javax.servlet.ServletContextEvent;
import javax.ws.rs.ext.Provider;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static net.talpidae.base.util.injector.BindingInspector.getTargetClass;


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

            for (Injector injector = childInjector; injector != null; injector = injector.getParent())
            {
                // inspired by GuiceResteasyBootstrapServletContextListener's ModuleProcessor
                // but doesn't register Resource interfaces
                val resources = new ArrayList<Binding<?>>();
                for (val binding : injector.getBindings().values())
                {
                    final Class<?> type = binding.getKey().getTypeLiteral().getRawType();
                    if (type != null)
                    {
                        val targetType = getTargetClass(binding);
                        if (GetRestful.isRootResource(type)
                                || (targetType != null
                                && !targetType.isInterface()
                                && GetRestful.isRootResource(targetType)
                                && targetType.getAnnotation(Resource.class) != null))
                        {
                            // register resources after providers
                            log.debug("rest resource: {} bound to {}", type.getName(), targetType.getName());
                            resources.add(binding);
                        }

                        if (type.isAnnotationPresent(Provider.class))
                        {
                            log.debug("rest provider: {}", type.getName());
                            providerFactory.registerProviderInstance(binding.getProvider().get());
                        }
                    }
                }

                for (val binding : resources)
                {
                    val type = binding.getKey().getTypeLiteral().getType();
                    val resourceFactory = new GuiceResourceFactory(binding.getProvider(), (Class) type);
                    registry.addResourceFactory(resourceFactory);
                }
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce)
    {
        super.contextDestroyed(sce);
    }
}
