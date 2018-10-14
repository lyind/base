package net.talpidae.base.util.injector;

import com.google.inject.Binding;
import com.google.inject.Injector;

import org.jboss.resteasy.util.GetRestful;

import java.lang.reflect.Modifier;

import javax.ws.rs.ext.Provider;

import lombok.val;

import static net.talpidae.base.util.injector.BindingInspector.getTargetClass;


public final class JaxRsBindingInspector
{
    private JaxRsBindingInspector()
    {

    }

    /**
     * Visit all JAX-RS Resource and Provider bindings.
     */
    public static void visitJaxRsBindings(Injector injector, ResourceBindingVisitor resourceVisitor, ProviderBindingVisitor providerVisitor)
    {
        for (; injector != null; injector = injector.getParent())
        {
            injector.getBindings().forEach((key, binding) ->
            {
                final Class<?> type = key.getTypeLiteral().getRawType();
                if (type != null)
                {
                    val isTypeResource = GetRestful.isRootResource(type);
                    if (isTypeResource)
                    {
                        val targetType = getTargetClass(binding);
                        if (targetType != null)
                        {
                            // linked or provider binding, ie. bind(IBlub.class).to(BlubImpl.class)
                            if (isConcrete(targetType))
                            {
                                resourceVisitor.accept(binding, type, targetType);
                            }
                        }
                        else if (isConcrete(type))
                        {
                            // direct binding, eg. bind(BlablaImpl.class)
                            resourceVisitor.accept(binding, type, type);
                        }
                    }

                    if (type.isAnnotationPresent(Provider.class) && isConcrete(type))
                    {
                        providerVisitor.accept(binding, type);
                    }
                }
            });
        }
    }


    private static boolean isConcrete(Class<?> type)
    {
        return !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }


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
}
