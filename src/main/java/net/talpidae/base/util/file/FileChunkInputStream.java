package net.talpidae.base.util.file;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;


/**
 * An FileInputStream decorator that only delivers part of the InputStream it wraps.
 */
@Slf4j
public class FileChunkInputStream extends InputStream
{
    private final long begin;

    private final long end;

    private final FileInputStream source;


    /**
     * FileInputStream for part of another FileInputStream. Allows reading from begin to end (exclusively).
     */
    public FileChunkInputStream(FileInputStream source, long begin, long end) throws FileNotFoundException
    {
        this.source = source;

        if (begin < 0 || (end >= 0 && end < begin))
        {
            throw new IllegalArgumentException("invalid chunk parameters begin/end: begin=" + Long.toString(begin) + ", end=" + Long.toString(end));
        }

        this.begin = begin;
        this.end = end;
    }


    @Override
    public long skip(long bytes) throws IOException
    {
        val position = seekToBeginPosition();

        return (position >= end) ? 0 : source.skip(Math.min(end - position, bytes));
    }


    @Override
    public int read() throws IOException
    {
        return (seekToBeginPosition() < end) ? source.read() : -1;
    }


    @Override
    public int read(byte b[]) throws IOException
    {
        return read(b, 0, b.length);
    }


    @Override
    public int read(byte b[], int off, int len) throws IOException
    {
        val allowed = getAvailableLimit();
        if (allowed <= 0 && len > 0)
        {
            return -1;
        }

        return source.read(b, off, Math.min(allowed, len));
    }


    @Override
    public int available() throws IOException
    {
        return (int) Math.min(source.available(), getAvailableLimit());
    }


    private int getAvailableLimit() throws IOException
    {
        return (int) Math.max(0, end - seekToBeginPosition());
    }


    /**
     * Ensure that we have reached at least begin position.
     */
    private long seekToBeginPosition() throws IOException
    {
        val position = source.getChannel().position();
        if (position < begin)
        {
            val diff = begin - position;
            val skipped = source.skip(diff);
            if (skipped != diff)
            {
                throw new IOException("failed to skip to begin: begin=" + Long.toString(begin) + ", position=" + Long.toString(position) + ", skipped=" + Long.toString(skipped));
            }

            return begin;
        }

        return position;
    }
}
