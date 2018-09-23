package net.talpidae.base.server;

import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.annotation.HandlesTypes;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


/**
 * ServletExtension that registers the RestBootstrapServletInitializer with the deployment.
 */
@Slf4j
public class RestBootstrapServletExtension implements ServletExtension
{
    private static Set<Class<?>> getHandlesTypes(Class<?> initializerClass)
    {
        val handlesTypes = initializerClass.getAnnotation(HandlesTypes.class);
        return handlesTypes != null
                ? Sets.newHashSet(handlesTypes.value())
                : Collections.emptySet();
    }

    @Override
    public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext)
    {
        try
        {
            val initializerClass = RestBootstrapServletInitializer.class;

            val initializerInfo = new ServletContainerInitializerInfo(
                    initializerClass,
                    deploymentInfo.getClassIntrospecter().createInstanceFactory(initializerClass),
                    getHandlesTypes(initializerClass));

            deploymentInfo.addServletContainerInitializer(initializerInfo);
        }
        catch (NoSuchMethodException e)
        {
            log.error("failed to bootstrap RESTEasy: {}", e.getMessage(), e);
        }
    }
}
