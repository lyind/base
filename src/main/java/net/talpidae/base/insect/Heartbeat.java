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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.config.SlaveSettings;
import net.talpidae.base.insect.exchange.message.MappingPayload;

import java.io.IOException;


@Slf4j
class Heartbeat implements CloseableRunnable
{
    private final Insect<? extends SlaveSettings> insect;

    @Getter
    private boolean isRunning = false;

    private Thread executingThread;


    Heartbeat(Insect<? extends SlaveSettings> insect)
    {
        this.insect = insect;
    }


    @Override
    public void run()
    {
        // Allow for some intrinsic balancing and make the heartbeat
        // interval depend more heavily on the node's load.
        executingThread = Thread.currentThread();
        synchronized (this)
        {
            isRunning = true;
            notifyAll();
        }

        try
        {
            while (!Thread.interrupted())
            {
                Thread.sleep(insect.getSettings().getPulseDelay());
                Thread.yield(); // give other threads a chance to delay us

                // notify upstream queen about our existence and route
                sendHeartbeat();
            }
        }
        catch (InterruptedException e)
        {
            log.warn("heartbeat worker interrupted");
        }
    }


    private void sendHeartbeat()
    {
        val heartBeatMapping = MappingPayload.builder()
                .port(insect.getSettings().getBindAddress().getPort())
                .host(insect.getSettings().getBindAddress().getHostString())
                .route(insect.getSettings().getRoute())
                .build();

        for (val remote : insect.getSettings().getRemotes())
        {
            insect.addMessage(remote, heartBeatMapping);
        }
    }


    @Override
    public void close() throws IOException
    {
        if (executingThread != null)
        {
            executingThread.interrupt();
        }
    }
}
