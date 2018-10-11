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

import net.talpidae.base.util.exception.DefaultUncaughtExceptionHandler;

import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.Map;
import java.util.logging.LogManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.jul.LevelChangePropagator;
import ch.qos.logback.classic.pattern.ClassOfCallerConverter;
import ch.qos.logback.classic.pattern.MethodOfCallerConverter;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.hook.DefaultShutdownHook;
import ch.qos.logback.core.util.Duration;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

import static com.google.common.base.Strings.nullToEmpty;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;


/**
 * Configure global logging using TinyLog with SLF4J bridge.
 */
public class DefaultLogbackLoggingConfigurer implements LoggingConfigurer
{
    /**
     * If the environment contains a variable named LOG_MULTILINE_SUPPRESS all but the terminating '\n' character
     * in log messages are replaced by '\r' to ease multi-line log shipping.
     */
    public static final String MULTILINE_WORKAROUND_NAME = "LOG_MULTILINE_SUPPRESS";

    @Getter
    @Setter(AccessLevel.PROTECTED)
    private int limitStackTraceLines = 86;

    @Getter
    @Setter(AccessLevel.PROTECTED)
    private Map<String, Level> packageToLevel = Collections.emptyMap();

    @Getter
    @Setter(AccessLevel.PROTECTED)
    private Level defaultLevel;

    @Getter
    private LoggerContext context;

    @Getter
    @Setter(AccessLevel.PROTECTED)
    private LayoutBase<ILoggingEvent> layout;


    public Level getDefaultLevel()
    {
        val level = defaultLevel;
        return level != null ? level : Level.DEBUG;
    }

    @Override
    public void configure()
    {
        context = (LoggerContext) LoggerFactory.getILoggerFactory();

        context.reset();
        context.setMaxCallerDataDepth(limitStackTraceLines);

        final Layout<ILoggingEvent> layout;
        if (this.layout != null)
        {
            // overridden by child class
            layout = this.layout;
        }
        else if (System.getenv(MULTILINE_WORKAROUND_NAME) != null)
        {
            // include LF -> CR converter (for easy ELK stack multi-line support)
            layout = this.layout = new MultilineLayout(context);
        }
        else
        {
            layout = this.layout = new DefaultLayout(context);
        }
        layout.setContext(context);
        layout.start();

        val shutdownHook = new DefaultShutdownHook();
        shutdownHook.setContext(context);
        shutdownHook.setDelay(Duration.buildBySeconds(8.5));

        // aligned after ShutdownHookAction
        val hookThread = new Thread(shutdownHook, "Logging shutdown hook [" + context.getName() + "]");
        context.putObject(CoreConstants.SHUTDOWN_HOOK_THREAD, hookThread);
        Runtime.getRuntime().addShutdownHook(hookThread);

        val encoder = new LayoutWrappingEncoder<ILoggingEvent>();
        encoder.setContext(context);
        encoder.setImmediateFlush(false);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.setLayout(layout);
        encoder.start();

        val consoleAppender = new ConsoleAppender<ILoggingEvent>();
        consoleAppender.setContext(context);
        consoleAppender.setName("console");
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        val logger = context.getLogger(ROOT_LOGGER_NAME);
        logger.setLevel(getDefaultLevel());
        logger.setAdditive(false);
        logger.addAppender(consoleAppender);

        val levelChangePropagator = new LevelChangePropagator();
        levelChangePropagator.setContext(context);
        levelChangePropagator.setResetJUL(true);
        levelChangePropagator.start();
        context.addListener(levelChangePropagator);

        for (val entry : getPackageToLevel().entrySet())
        {
            val configuredLogger = context.getLogger(entry.getKey());
            configuredLogger.setLevel(entry.getValue());
        }

        context.start();

        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        System.setProperty("org.jboss.logging.provider", "slf4j");

        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());
    }

    @Override
    public void putContext(String key, String value)
    {
        GlobalAndRequestScopedMDC.putGlobal(key, value);
    }

    @Override
    public void removeContext(String key)
    {
        GlobalAndRequestScopedMDC.removeGlobal(key);
    }

    @Override
    public String getContext(String key)
    {
        return GlobalAndRequestScopedMDC.getGlobal(key);
    }


    public static class DefaultLayout extends LayoutBase<ILoggingEvent>
    {
        private final ThrowableProxyConverter throwableConverter = new ThrowableProxyConverter();

        private final ClassOfCallerConverter classOfCallerConverter = new ClassOfCallerConverter();

        private final MethodOfCallerConverter methodOfCallerConverter = new MethodOfCallerConverter();

        private final DateTimeFormatter dateTimeFormatter;

        public DefaultLayout(LoggerContext context)
        {
            throwableConverter.setContext(context);
            throwableConverter.start();

            classOfCallerConverter.setContext(context);
            classOfCallerConverter.setOptionList(Collections.singletonList("0"));
            classOfCallerConverter.start();

            methodOfCallerConverter.setContext(context);
            methodOfCallerConverter.start();

            // "yyyy-MM-dd HH:mm:ss.SSS", no need to parse
            dateTimeFormatter = new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE)
                    .appendLiteral(' ')
                    .appendPattern("HH:mm:ss")
                    // optional nanos with 3 digits (including decimal point)
                    .appendFraction(ChronoField.NANO_OF_SECOND, 3, 3, true)
                    .toFormatter();
        }

        public String doLayout(ILoggingEvent event)
        {
            return build(event).toString();
        }

        protected StringBuilder build(ILoggingEvent event)
        {
            // pattern "%d{YYYY-MM-dd HH:mm:ss.SSS} [%X{insectName}] [%t] %C{1].%M\\(\\) %p: %m%throwable{86}\n"
            return new StringBuilder(128)
                    .append(dateTimeFormatter.format(Instant.ofEpochMilli(event.getTimeStamp()).atOffset(ZoneOffset.UTC)))
                    .append(" [")
                    .append(nullToEmpty(GlobalAndRequestScopedMDC.getGlobal("insectName"))) // MDC: insectName
                    .append("] [")
                    .append(event.getThreadName())
                    .append("] ")
                    .append(classOfCallerConverter.convert(event))
                    .append('.')
                    .append(methodOfCallerConverter.convert(event))
                    .append("() ")
                    .append(event.getLevel())
                    .append(": ")
                    .append(event.getFormattedMessage())
                    .append('\n')
                    .append(throwableConverter.convert(event));
        }
    }


    public static class MultilineLayout extends DefaultLayout
    {
        public MultilineLayout(LoggerContext context)
        {
            super(context);
        }

        @Override
        protected StringBuilder build(ILoggingEvent event)
        {
            val builder = super.build(event);

            // replace all CRLF or LF with CR inside the relevant StringBuilder section
            val end = builder.length();
            for (int index = builder.indexOf("\n", 0);
                 index >= 0 && index < end - 1; // leave out last LF
                 index = builder.indexOf("\n", index + 1))
            {
                builder.setCharAt(index, '\r');
            }

            return builder;
        }
    }
}
