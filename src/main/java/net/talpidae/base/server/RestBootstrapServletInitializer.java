package net.talpidae.base.server;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import net.talpidae.base.resource.DefaultRestApplication;

import org.jboss.resteasy.plugins.servlet.ResteasyServletInitializer;

import java.util.Collections;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.ws.rs.ApplicationPath;

import lombok.extern.slf4j.Slf4j;


/**
 * ServletContainerInitializer which registers servlets for all bound Application instances.
 */
@Slf4j
@Singleton
public class RestBootstrapServletInitializer extends ResteasyServletInitializer
{
    private final Injector parentInjector;

    private final GuiceServletContextListener servletContextListener;

    @Inject
    public RestBootstrapServletInitializer(Injector parentInjector, GuiceServletContextListener servletContextListener)
    {
        this.parentInjector = parentInjector;
        this.servletContextListener = servletContextListener;
    }


    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException
    {
        boolean haveApplication = false;
        for (Injector injector = parentInjector; injector != null; injector = injector.getParent())
        {
            for (final Binding<?> binding : injector.getBindings().values())
            {
                final Class<?> beanClass = binding.getKey().getTypeLiteral().getRawType();
                if (beanClass != null
                        && beanClass.isAnnotationPresent(ApplicationPath.class)
                        && beanClass != DefaultRestApplication.class)
                {
                    registerServlet(beanClass, servletContext);
                    haveApplication = true;
                }
            }
        }

        // if no application classes were found, register DefaultRestApplication, mapped to "/*"
        if (!haveApplication)
        {
            registerServlet(DefaultRestApplication.class, servletContext);
        }

        // add ServletContextListener that initializes a Guice child parentInjector per Servlet
        servletContext.addListener(servletContextListener);
    }


    private void registerServlet(Class<?> applicationClass, ServletContext ctx)
    {
        log.debug("register servlet for: {}", applicationClass.getName());
        register(applicationClass, Collections.emptySet(), Collections.emptySet(), ctx);
    }
}
