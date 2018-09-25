package net.talpidae.base.util.injector;

import com.google.inject.Binding;
import com.google.inject.Injector;

import org.jboss.resteasy.util.GetRestful;

import javax.ws.rs.ext.Provider;

import lombok.val;

import static net.talpidae.base.util.injector.BindingInspector.getTargetClass;


public final class JaxRsBindingInspector
{
    @FunctionalInterface
    public interface ResourceBindingVisitor
    {
        void accept(Binding<?> binding, Class<?> type, Class<?> targetType);
    }


    @FunctionalInterface
    public interface ProviderBindingVisitor
    {
        void accept(Binding<?> binding, Class<?> type);
    }


    /**
     * Visit all JAX-RS Resource and Provider bindings.
     */
    public static void visitJaxRsBindings(Injector injector, ResourceBindingVisitor resourceVisitor, ProviderBindingVisitor providerVisitor)
    {
        for (; injector != null; injector = injector.getParent())
        {
            for (val binding : injector.getBindings().values())
            {
                final Class<?> type = binding.getKey().getTypeLiteral().getRawType();
                if (type != null)
                {
                    val isTypeResource = GetRestful.isRootResource(type);
                    if (isTypeResource)
                    {
                        val targetType = getTargetClass(binding);
                        if (targetType != null && !targetType.isInterface())
                        {
                            // register resources after providers
                            resourceVisitor.accept(binding, type, targetType);
                        }
                    }

                    if (type.isAnnotationPresent(Provider.class))
                    {
                        providerVisitor.accept(binding, type);
                    }
                }
            }
        }
    }


    private JaxRsBindingInspector()
    {

    }
}
