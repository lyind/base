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

package net.talpidae.base;

import com.google.common.eventbus.EventBus;
import com.google.inject.*;
import com.google.inject.servlet.ServletModule;
import com.squarespace.jersey2.guice.JerseyGuiceModule;
import com.squarespace.jersey2.guice.JerseyGuiceUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.InsectModule;
import net.talpidae.base.resource.JerseySupportModule;
import net.talpidae.base.server.ServerModule;
import net.talpidae.base.util.Application;
import net.talpidae.base.util.BaseArguments;
import org.pmw.tinylog.Configurator;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;


@Slf4j
@RequiredArgsConstructor
public class Base extends AbstractModule
{
    private static final byte[] LOCK = new byte[0];

    private static volatile boolean isInitialized = false;

    private final String[] args;


    public static Application initializeApp(String[] args, AbstractModule applicationModule) throws IllegalStateException
    {
        synchronized (LOCK)
        {
            if (!isInitialized)
            {
                initializeLogging();

                val modules = new ArrayList<Module>();

                modules.add(new JerseyGuiceModule("__HK2_Generated_0"));
                modules.add(new JerseySupportModule());
                modules.add(new ServletModule());
                modules.add(new ServerModule());
                modules.add(new InsectModule());
                modules.add(new Base(args));
                modules.add(applicationModule);

                val injector = Guice.createInjector(modules);
                JerseyGuiceUtils.install(injector);

                isInitialized = true;

                return injector.getInstance(Application.class);
            }
            else
            {
                throw new IllegalStateException("application is already initialized");
            }
        }
    }


    private static void initializeLogging()
    {
        Configurator.currentConfig()
                .level(org.pmw.tinylog.Level.DEBUG)
                .formatPattern("{date:yyyy-MM-dd HH:mm:ss} [{thread}] {class}.{method}() {level}: {message}")
                .activate();

        LogManager.getLogManager().reset();

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        Logger.getLogger("global").setLevel(Level.FINEST);
    }


    @Override
    protected void configure()
    {
        requireBinding(Application.class);
    }


    // use guava event bus
    @Singleton
    @Provides
    public EventBus getEventBus()
    {
        return new EventBus();
    }

    // supply program arguments to interested classes
    @Singleton
    @Provides
    public BaseArguments getBaseArguments()
    {
        return new BaseArguments(args);
    }
}
