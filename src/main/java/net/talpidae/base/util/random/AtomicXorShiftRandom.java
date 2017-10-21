package net.talpidae.base.util.random;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;


/**
 * To make performance critical operations a this allows pseudo-random number generation using a simpler xorshift algorithm.
 * <p>
 * AtomicXorShiftRandom.nextInt() is thread-safe.
 */
public final class AtomicXorShiftRandom extends Random
{
    private volatile AtomicLong x64 = new AtomicLong(System.nanoTime());

    @Override
    public int nextInt(int limit)
    {
        // calculate next pseudo-random number until we can place our version
        // this is to ensure each step is only issued once
        long last;
        long next;
        do
        {
            last = x64.get();
            next = last;

            next ^= (next << 21);
            next ^= (next >>> 35);
            next ^= (next << 4);
        }
        while (!x64.compareAndSet(last, next));

        final int result = (int) next % limit;

        return (result < 0) ? -result : result;
    }
}