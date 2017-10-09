package net.talpidae.base.util.lifecycle;

import com.google.common.eventbus.EventBus;
import lombok.extern.slf4j.Slf4j;
import net.talpidae.base.event.Shutdown;

import javax.inject.Inject;
import javax.inject.Singleton;


@Slf4j
@Singleton
public class DefaultShutdownHook extends ShutdownHook
{
    @Inject
    public DefaultShutdownHook(EventBus eventBus, final CloseOnShutdown closeOnShutdown)
    {
        super(() ->
        {
            // by default, just tell all listening components that we are supposed to shut down
            eventBus.post(new Shutdown());

            // then
            closeOnShutdown.close();
        });
    }
}