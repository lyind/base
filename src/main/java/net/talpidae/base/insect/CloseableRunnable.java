package net.talpidae.base.insect;

import java.io.Closeable;


public interface CloseableRunnable extends Runnable, Closeable
{
    boolean isRunning();
}
