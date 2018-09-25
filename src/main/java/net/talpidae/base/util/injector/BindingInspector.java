package net.talpidae.base.util.injector;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.multibindings.MapBinderBinding;
import com.google.inject.multibindings.MultibinderBinding;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
import com.google.inject.multibindings.OptionalBinderBinding;
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
            return binding.acceptTargetVisitor(new BindingInspectorVisitor<>());
        }

        return null;
    }


    private static final class BindingInspectorVisitor<T, C extends Class<? extends T>> extends DefaultBindingTargetVisitor<T, C> implements MultibindingsTargetVisitor<T, C>
    {
        @SuppressWarnings("unchecked")
        @Override
        public C visit(LinkedKeyBinding<? extends T> binding)
        {
            return (C) binding.getLinkedKey().getTypeLiteral().getRawType();
        }

        @Override
        public C visit(MultibinderBinding<? extends T> multibinding)
        {
            return null;
        }

        @Override
        public C visit(MapBinderBinding<? extends T> mapbinding)
        {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public C visit(OptionalBinderBinding<? extends T> optionalbinding)
        {
            val actualBinding = optionalbinding.getActualBinding();
            if (actualBinding != null)
            {
                return (C) actualBinding.acceptTargetVisitor(new BindingInspectorVisitor<>());
            }

            return null;
        }
    }
}
