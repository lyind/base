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

package net.talpidae.base.insect;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.TimeUnit;

import static java.lang.Thread.State.TERMINATED;


@Slf4j
class InsectWorker extends Thread
{
    private static final long WORKER_TIMEOUT = TimeUnit.MILLISECONDS.toNanos(5000L);


    private InsectWorker(Runnable task)
    {
        super(task);
    }

    public static InsectWorker start(CloseableRunnable task, String name)
    {
        val worker = new InsectWorker(task);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.setName(name);

        val before = System.nanoTime();
        val timeout = before + WORKER_TIMEOUT;
        worker.start();
        try
        {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (task)
            {
                while (!task.isRunning())
                {
                    val remaining = System.nanoTime() - timeout;
                    if (remaining <= 0)
                        break;

                    task.wait(remaining);
                }
            }

            log.debug("worker {} started within {}ms", name, Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before)));
        }
        catch (InterruptedException e)
        {
            log.warn("wait for startup of {} was interrupted", name);
        }

        return worker;
    }

    public boolean shutdown()
    {
        try
        {
            interrupt();
            join(WORKER_TIMEOUT);
        }
        catch (InterruptedException e)
        {
            interrupt();
        }

        val isTerminated = getState() == TERMINATED;
        if (!isTerminated)
        {
            log.warn("failed to stop worker {}", getName());
        }

        return isTerminated;
    }
}
