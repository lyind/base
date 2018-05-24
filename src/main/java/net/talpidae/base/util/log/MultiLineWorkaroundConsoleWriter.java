package net.talpidae.base.util.log;

import lombok.val;
import org.pmw.tinylog.Configuration;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.LogEntry;
import org.pmw.tinylog.writers.LogEntryValue;
import org.pmw.tinylog.writers.PropertiesSupport;
import org.pmw.tinylog.writers.Writer;

import java.io.PrintStream;
import java.util.EnumSet;
import java.util.Set;


/**
 * Writes log entries to the console converting '\n' to '\r' to avoid multi-line issues.
 */
@PropertiesSupport(name = "multiline_console", properties = {})
public final class MultiLineWorkaroundConsoleWriter implements Writer
{
    @Override
    public Set<LogEntryValue> getRequiredLogEntryValues()
    {
        return EnumSet.of(LogEntryValue.LEVEL, LogEntryValue.RENDERED_LOG_ENTRY);
    }

    @Override
    public void init(final Configuration configuration)
    {
        // Do nothing
    }

    @Override
    public void write(final LogEntry logEntry)
    {
        val chars = logEntry.getRenderedLogEntry().toCharArray();

        final int end = chars.length - 1;  // do not touch message delimiter
        for (int i = 0; i < end; ++i)
        {
            if (chars[i] == '\n')
                chars[i] = '\r';
        }

        getPrintStream(logEntry.getLevel()).print(chars);
    }

    @Override
    public void flush()
    {
    }

    @Override
    public void close()
    {
    }

    private static PrintStream getPrintStream(final Level level)
    {
        if (level == Level.ERROR || level == Level.WARNING)
        {
            return System.err;
        }
        else
        {
            return System.out;
        }
    }
}