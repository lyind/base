package net.talpidae.base.util.injector;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.LinkedKeyBinding;

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
     * Get the interfaces that the bound class implements.
     */
    public static <T> List<Class<?>> getLinkedClassInterfaces(Injector injector, Class<T> keyClass)
    {
        for (; injector != null; injector = injector.getParent())
        {
            val binding = injector.getExistingBinding(Key.get(keyClass));
            if (binding != null)
            {
                val targetInterfaces = binding.acceptTargetVisitor(new DefaultBindingTargetVisitor<T, Class<?>[]>()
                {
                    @Override
                    public Class<?>[] visit(LinkedKeyBinding<? extends T> binding)
                    {
                        return binding.getLinkedKey().getTypeLiteral().getRawType().getInterfaces();
                    }
                });

                return targetInterfaces != null ? Arrays.asList(targetInterfaces) : Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }
}
