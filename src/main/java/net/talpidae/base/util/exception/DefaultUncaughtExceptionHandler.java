package net.talpidae.base.util.exception;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class DefaultUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler
{
    @Override
    public void uncaughtException(Thread thread, Throwable throwable)
    {
        log.error("uncaught: {}", throwable.getMessage(), throwable);
    }
}