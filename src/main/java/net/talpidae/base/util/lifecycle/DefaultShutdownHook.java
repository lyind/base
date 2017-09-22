package net.talpidae.base.util.lifecycle;

import com.google.common.eventbus.EventBus;

import net.talpidae.base.event.Shutdown;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Singleton
public class DefaultShutdownHook extends ShutdownHook
{
    @Inject
    public DefaultShutdownHook(EventBus eventBus)
    {
        super(() ->
        {
            // by default, just tell all listening components that we are supposed to shut down
            eventBus.post(new Shutdown());
        });
    }
}