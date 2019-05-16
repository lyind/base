package net.talpidae.base.server;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.util.injector.JaxRsBindingInspector;
import org.jboss.resteasy.plugins.guice.GuiceResourceFactory;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import javax.servlet.ServletContextEvent;
import java.util.ArrayList;


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
        val ctx = event.getServletContext();

        // disable class-path scanning
        ResteasyProviderFactory.setRegisterBuiltinByDefault(false);
        ctx.setInitParameter(ResteasyContextParameters.RESTEASY_USE_BUILTIN_PROVIDERS, "false");

        super.contextInitialized(event);

        val deployment = (ResteasyDeployment) ctx.getAttribute(ResteasyDeployment.class.getName());
        if (deployment != null)
        {
            log.debug("bootstrapping RestModule");

            val registry = deployment.getRegistry();
            val providerFactory = deployment.getProviderFactory();
            providerFactory.setRegisterBuiltins(false);
            providerFactory.setBuiltinsRegistered(true);

            val childInjector = parentInjector != null
                    ? parentInjector.createChildInjector()
                    : Guice.createInjector();

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
