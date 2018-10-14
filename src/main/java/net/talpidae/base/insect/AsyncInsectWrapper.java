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
import net.talpidae.base.util.log.LoggingConfigurer;

import java.util.Objects;

import static net.talpidae.base.util.log.LoggingConfigurer.CONTEXT_INSECT_NAME_KEY;


@Slf4j
public class AsyncInsectWrapper<T extends Insect<?>> implements CloseableRunnable
{
    @Getter
    private final T insect;

    private final LoggingConfigurer loggingConfigurer;

    private InsectWorker insectWorker;


    AsyncInsectWrapper(T insect, LoggingConfigurer loggingConfigurer)
    {
        this.insect = insect;
        this.loggingConfigurer = loggingConfigurer;
    }


    @Override
    public void run()
    {
        // use actual insect name from now on (for logging)
        updateLoggingContext();

        // spawn worker thread
        insectWorker = InsectWorker.start(insect, insect.getClass().getSimpleName());
    }


    @Override
    public void close()
    {
        if (insectWorker.shutdown())
        {
            insectWorker = null;
            log.debug("worker shut down");
        }
    }


    public boolean isRunning()
    {
        return insectWorker != null && insect.isRunning();
    }


    private void updateLoggingContext()
    {
        val actualInsectName = getInsect().getSettings().getName();
        val currentInsectName = loggingConfigurer.getContext(CONTEXT_INSECT_NAME_KEY);
        if (!Objects.equals(actualInsectName, currentInsectName))
        {
            log.debug("context: {} changed from {} to {}",
                    CONTEXT_INSECT_NAME_KEY,
                    loggingConfigurer.getContext(CONTEXT_INSECT_NAME_KEY),
                    actualInsectName);

            loggingConfigurer.putContext(CONTEXT_INSECT_NAME_KEY, actualInsectName);
        }
    }
}
