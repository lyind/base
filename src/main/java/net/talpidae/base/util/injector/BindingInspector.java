package net.talpidae.base.util.injector;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderKeyBinding;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.val;


public final class BindingInspector
{
    private BindingInspector()
    {

    }


    /**
     * Get the target class's interfaces for any binding of @param keyClass.
     */
    public static <T> List<Class<?>> getTargetClassInterfaces(Injector injector, Class<T> keyClass)
    {
        val target = getTargetClass(injector, keyClass);
        return target != null ? Arrays.asList(target.getInterfaces()) : Collections.emptyList();
    }


    /**
     * Get the target class for any binding of @param keyClass.
     */
    public static <T> Class<? extends T> getTargetClass(Injector injector, Class<T> keyClass)
    {
        for (; injector != null; injector = injector.getParent())
        {
            val targetClass = getTargetClass(injector.getExistingBinding(Key.get(keyClass)));
            if (targetClass != null)
            {
                return targetClass;
            }
        }

        return null;
    }

    /**
     * Get the target class of the specified @param binding.
     */
    public static <T> Class<? extends T> getTargetClass(Binding<T> binding)
    {
        if (binding != null)
        {
            return binding.acceptTargetVisitor(new DefaultBindingTargetVisitor<T, Class<? extends T>>()
            {
                @SuppressWarnings("unchecked")
                @Override
                public Class<? extends T> visit(LinkedKeyBinding<? extends T> binding)
                {
                    return (Class<? extends T>) binding.getLinkedKey().getTypeLiteral().getRawType();
                }

                @Override
                public Class<? extends T> visit(ProviderKeyBinding<? extends T> providerKeyBinding)
                {
                    return visitOther(providerKeyBinding);
                }
            });
        }

        return null;
    }
}
