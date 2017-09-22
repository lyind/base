package net.talpidae.base.util.lifecycle;


/**
 * Applications may override this to register one shutdown handler (using Runtime.registerShutdownHook()).
 */
public abstract class ShutdownHook extends Thread
{
    public ShutdownHook(Runnable hook)
    {
        super(hook);
    }
}
