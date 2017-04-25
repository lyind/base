package net.talpidae.base.util.file;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;


/**
 * A FileInputStream like stream for a part of a file.
 *
 * @todo Also override methods in getChannel() and getFD()?
 */
@Slf4j
public class FileChunkInputStream extends FileInputStream
{
    private final long begin;

    private final long end;


    /**
     * File input stream for part of a file. Allows reading from begin to end (exclusively).
     */
    public FileChunkInputStream(File file, long begin, long end) throws FileNotFoundException
    {
        super(file);

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

        return (position >= end) ? 0 : super.skip(Math.min(end - position, bytes));
    }


    @Override
    public int read() throws IOException
    {
        return (seekToBeginPosition() < end) ? super.read() : -1;
    }


    @Override
    public int read(byte b[]) throws IOException
    {
        return read(b, 0, b.length);
    }


    @Override
    public int read(byte b[], int off, int len) throws IOException
    {
        return super.read(b, off, Math.min(available(), len));
    }


    @Override
    public int available() throws IOException
    {
        return (int) Math.max(0, end - seekToBeginPosition());
    }


    /**
     * Ensure that we have reached at least begin position.
     */
    private long seekToBeginPosition() throws IOException
    {
        val position = super.getChannel().position();
        if (position < begin)
        {
            val diff = begin - position;
            val skipped = super.skip(diff);
            if (skipped != diff)
            {
                throw new IOException("failed to skip to begin: begin=" + Long.toString(begin) + ", position=" + Long.toString(position) + ", skipped=" + Long.toString(skipped));
            }

            return begin;
        }

        return position;
    }


    /**
     * Prevent user from calling this. Not implemented, yet.
     */
    @Override
    public FileChannel getChannel()
    {
        throw new IllegalArgumentException("getChannel() not implemented for FileChunkInputStream");
    }
}
