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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import com.squarespace.jersey2.guice.JerseyGuiceModule;
import com.squarespace.jersey2.guice.JerseyGuiceUtils;

import net.talpidae.base.client.ClientModule;
import net.talpidae.base.database.DataBaseModule;
import net.talpidae.base.insect.InsectModule;
import net.talpidae.base.mapper.MapperModule;
import net.talpidae.base.resource.JerseySupportModule;
import net.talpidae.base.server.ServerModule;
import net.talpidae.base.util.Application;
import net.talpidae.base.util.BaseArguments;
import net.talpidae.base.util.auth.scope.AuthScoped;
import net.talpidae.base.util.auth.scope.AuthenticatedRunnable;
import net.talpidae.base.util.auth.scope.GuiceAuthScope;
import net.talpidae.base.util.log.DefaultTinyLogLoggingConfigurer;
import net.talpidae.base.util.log.LoggingConfigurer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
@RequiredArgsConstructor
public class Base extends AbstractModule
{
    private static final byte[] LOCK = new byte[0];

    private static final GuiceAuthScope authScope = new GuiceAuthScope();

    private static volatile boolean isInitialized = false;

    private final String[] args;

    @Getter
    private final LoggingConfigurer loggingConfigurer;

    public static Application initializeApp(String[] args, AbstractModule applicationModule) throws IllegalStateException
    {
        return initializeApp(args, Collections.singletonList(applicationModule), null);
    }

    public static Application initializeApp(String[] args, AbstractModule applicationModule, LoggingConfigurer loggingConfigurer) throws IllegalStateException
    {
        return initializeApp(args, Collections.singletonList(applicationModule), loggingConfigurer);
    }

    public static Application initializeApp(String[] args, List<AbstractModule> applicationModules) throws IllegalStateException
    {
        return initializeApp(args, applicationModules, null);
    }

    public static Application initializeApp(String[] args, List<AbstractModule> applicationModules, LoggingConfigurer loggingConfigurer) throws IllegalStateException
    {
        synchronized (LOCK)
        {
            if (!isInitialized)
            {
                // initialize logging subsystem, use default if no override was provided
                if (loggingConfigurer == null)
                    loggingConfigurer = new DefaultTinyLogLoggingConfigurer();

                loggingConfigurer.configure();

                val modules = new ArrayList<Module>();

                modules.add(new JerseyGuiceModule("__HK2_Generated_0"));
                modules.add(new JerseySupportModule());
                modules.add(new ServletModule());
                modules.add(new Base(args, loggingConfigurer));

                // add user specified modules
                modules.addAll(applicationModules);

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

    /**
     * Decorate a runnable so that it is always run under AuthScope.
     */
    public static Runnable decorateWithAuthScope(Runnable runnable)
    {
        return new AuthenticatedRunnable(authScope, runnable);
    }

    @Override
    protected void configure()
    {
        requireBinding(Application.class);

        bindScope(AuthScoped.class, authScope);
        bind(GuiceAuthScope.class).toInstance(authScope);

        bind(LoggingConfigurer.class).toInstance(loggingConfigurer);

        install(new DataBaseModule());
        install(new MapperModule());
        install(new ServerModule());
        install(new InsectModule());

        install(new ClientModule());
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