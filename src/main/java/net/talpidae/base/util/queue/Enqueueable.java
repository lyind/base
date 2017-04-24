package net.talpidae.base.util.queue;

public interface Enqueueable<T>
{
    long getOffset();

    T getElement();
}
