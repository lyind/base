/*
 * Copyright (C) 2017  Jonas Zeiger <jonas.zeiger@talpidae.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.talpidae.base.util.thread;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.event.Shutdown;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.*;


@Slf4j
@Singleton
public class GeneralScheduler
{
    private final ScheduledExecutorService executorService;


    @Inject
    public GeneralScheduler(EventBus eventBus)
    {
        eventBus.register(this);

        val executor = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory(GeneralScheduler.class.getSimpleName()));
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);

        executorService = executor;
    }
    

    public ScheduledFuture<?> schedule(Runnable command)
    {
        return executorService.schedule(command, 0, TimeUnit.MILLISECONDS);
    }


    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
    {
        return executorService.schedule(command, delay, unit);
    }


    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit)
    {
        return executorService.schedule(callable, delay, unit);
    }


    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit)
    {
        return executorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }


    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit)
    {
        return executorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }


    @Subscribe
    public void shutdownEvent(Shutdown event)
    {
        executorService.shutdownNow();
        try
        {
            if (!executorService.awaitTermination(5L, TimeUnit.SECONDS))
            {
                log.warn("timeout waiting for GeneralScheduler shutdown");
            }
        }
        catch (InterruptedException e)
        {
            log.warn("interrupted while waiting for GeneralScheduler shutdown");
        }
    }
}
