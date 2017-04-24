package net.talpidae.base.util.queue;

import lombok.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;


public class ConcurrentArrayOffsetQueue<T>
{
    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L; // ECMAScript Number.MAX_SAFE_INTEGER

    private static final long START_OFFSET = 0;

    private final List<Enqueueable<T>> overflowResult = Collections.singletonList(new SimpleEnqueueable<T>(JS_MAX_SAFE_INTEGER, null));

    private final int length;

    private final AtomicReferenceArray<Enqueueable<T>> queue;

    // head of the queue (newest element)
    private final AtomicLong state;


    protected ConcurrentArrayOffsetQueue(int capacity)
    {
        queue = new AtomicReferenceArray<>(Math.max(2, capacity + 1));
        length = queue.length();
        state = new AtomicLong(0);
    }


    private static int getHead(long headAndTail)
    {
        return (int) (headAndTail >>> 32);
    }

    private static int getTail(long headAndTail)
    {
        return (int) headAndTail;
    }

    private static long combineHeadAndTail(int head, int tail)
    {
        return (((long) head) << 32) | tail;
    }


    /**
     * Add a new element to the queue.
     * <p>
     * May block on concurrent access by multiple writers.
     */
    public void add(@NonNull T element)
    {
        synchronized (queue)
        {
            val stateCopy = state.get();
            val headIndex = getHead(stateCopy);
            val tailIndex = getTail(stateCopy);

            val headElement = queue.get((headIndex + length - 1) % length);
            val newOffset = ((headElement != null) ? headElement.getOffset() : START_OFFSET) + 1;

            val newElement = new SimpleEnqueueable<T>(newOffset, element);
            val newHeadIndex = (headIndex + 1) % length;
            final int newTailIndex;
            if (newHeadIndex != tailIndex)
            {
                // only advance head index
                newTailIndex = tailIndex;
            }
            else
            {
                // "make room" for newly inserted head and advance head index
                newTailIndex = (headIndex + 2) % length;
            }

            state.set(combineHeadAndTail(newHeadIndex, newTailIndex));
            queue.set(headIndex, newElement);
        }
    }


    /**
     * Poll for news (non-blocking).
     *
     * @param offset The most recent offset returned by this method or JS_MAX_SAFE_INTEGER to restart from now.
     * @return One Enqueueable with offset == 0 in case of overflow,
     * one Enqueueable with element == null if empty result,
     * list of new Enqueueables since offset otherwise.
     */
    public List<Enqueueable<T>> pollSince(long offset)
    {
        val stateCopy = state.get();
        final int headIndex = getHead(stateCopy);

        // just return initial offset, user needs to poll again using that offset
        if (offset >= JS_MAX_SAFE_INTEGER)
        {
            val headElement = queue.get(headIndex);
            val startOffset = (headElement != null) ? headElement.getOffset() : START_OFFSET;

            // handle queue offset overflow by resetting it to initial state
            if (startOffset >= JS_MAX_SAFE_INTEGER)
            {
                synchronized (queue)
                {
                    state.set(0);
                    for (int i = 0; i < length; ++i)
                    {
                        queue.set(i, null);
                    }
                }

                return Collections.emptyList();
            }

            val startElement = new SimpleEnqueueable<T>(startOffset, null);

            return Collections.singletonList(startElement);
        }

        // pick first element and evaluate situation
        int i = getTail(stateCopy);
        val tailElement = queue.get(i);
        if (tailElement != null)
        {
            val tailOffset = tailElement.getOffset();
            val distanceToHead = (i < headIndex) ? headIndex - i : length - (i - headIndex);

            final int targetCapacity;
            if (offset + 1 < tailOffset)
            {
                // tail offset already greater than last polled offset: OVERFLOW
                // double check that head offset
                val previousHeadElement = queue.get((headIndex + length - 1) % length);
                if (previousHeadElement != null && previousHeadElement.getOffset() - (length - 1) > offset)
                {
                    return overflowResult;
                }
                else
                {
                    // need to wait until the queue is in sync with state
                    return Collections.emptyList();
                }
            }

            // advance to calculated index of first interesting element
            val distanceToFirstInterestingElement = Math.min(distanceToHead, Math.max(0, (int) (1 + offset - tailOffset)));

            i = (i + distanceToFirstInterestingElement) % length;
            targetCapacity = distanceToHead - distanceToFirstInterestingElement;
            if (targetCapacity > 0)
            {
                val elements = new ArrayList<Enqueueable<T>>(targetCapacity);
                long previousOffset = offset;
                for (; i != headIndex; i = (i + 1) % length)
                {
                    val element = queue.get(i);
                    val elementOffset = (element != null) ? element.getOffset() : START_OFFSET;

                    if (previousOffset >= elementOffset)
                    {
                        // fetched an element that has not yet been overwritten,
                        // don't wait for the new object to become visible
                        break;
                    }
                    else if (elementOffset - previousOffset != 1)
                    {
                        // queue overflow during retrieval
                        return overflowResult;
                    }
                    else
                    {
                        previousOffset = elementOffset;
                        elements.add(element);
                    }
                }

                return elements;
            }
        }

        // no news, yet
        return Collections.emptyList();
    }


    @ToString
    @RequiredArgsConstructor
    private static class SimpleEnqueueable<E> implements Enqueueable<E>
    {
        @Getter
        private final long offset;

        @Getter
        private final E element;
    }
}
