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

package net.talpidae.base.util.log;

import lombok.val;
import net.talpidae.base.util.exception.DefaultUncaughtExceptionHandler;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.LoggingContext;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 * Configure global logging using TinyLog with SLF4J bridge.
 */
public class DefaultTinyLogLoggingConfigurer implements LoggingConfigurer
{
    /**
     * If the environment contains a variable named LOG_MULTILINE_SUPPRESS all but the terminating '\n' character
     * in log messages are replaced by '\r' to ease multi-line log shipping.
     */
    public static final String MULTILINE_WORKAROUND_NAME = "LOG_MULTILINE_SUPPRESS";


    @Override
    public void configure()
    {
        val config = Configurator.currentConfig()
                .level(org.pmw.tinylog.Level.DEBUG)
                .maxStackTraceElements(86)
                .formatPattern("{date:yyyy-MM-dd HH:mm:ss} [{context:insectName}] [{thread}] {class}.{method}() {level}: {message}");

        if (System.getenv(MULTILINE_WORKAROUND_NAME) != null)
        {
            config.removeAllWriters();
            config.addWriter(new MultiLineWorkaroundConsoleWriter());
        }

        config.activate();

        LogManager.getLogManager().reset();

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        Logger.getLogger("global").setLevel(Level.FINEST);

        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());
    }


    @Override
    public void putContext(String key, String value)
    {
        LoggingContext.put(key, value);
    }


    @Override
    public void removeContext(String key)
    {
        LoggingContext.remove(key);
    }


    @Override
    public String getContext(String key)
    {
        return LoggingContext.get(key);
    }
}
