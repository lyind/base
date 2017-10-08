package net.talpidae.base.util.lifecycle;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import net.talpidae.base.event.ServerShutdown;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
@Singleton
public class CloseOnServerShutdown
{
    private final List<WeakReference<Closeable>> closeables = new ArrayList<>();


    @Inject
    public CloseOnServerShutdown(EventBus eventBus)
    {
        eventBus.register(this);
    }


    /**
     * Register Closeable object for cleanup on ServerShutdown event.
     */
    public <T extends Closeable> T add(T closeable)
    {
        closeables.add(new WeakReference<>(closeable));

        return closeable;
    }


    @Subscribe
    public void onServerShutdown(ServerShutdown shutdown)
    {
        for (val closeableRef : closeables)
        {
            val closeable = closeableRef.get();
            if (closeable != null)
            {
                try
                {
                    closeable.close();
                }
                catch (IOException e)
                {
                    log.error("exception closing {}: {}", closeable.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
    }
}
