package net.talpidae.base.util.lifecycle;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Singleton
public class CloseOnShutdown implements Closeable
{
    private final List<WeakReference<Closeable>> closeables = new ArrayList<>();


    /**
     * Register Closeable object for cleanup on ServerShutdown event.
     */
    public <T extends Closeable> T add(T closeable)
    {
        closeables.add(new WeakReference<>(closeable));

        return closeable;
    }


    @Override
    public void close()
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