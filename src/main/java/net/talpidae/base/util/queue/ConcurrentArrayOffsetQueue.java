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

    private final Enqueueable<T> overflowIndicator = new SimpleEnqueueable<>(JS_MAX_SAFE_INTEGER, null);

    private final List<Enqueueable<T>> overflowResult = Collections.singletonList(overflowIndicator);

    private final int length;

    private final AtomicReferenceArray<Enqueueable<T>> queue;

    // head of the queue (newest element)
    private final AtomicLong state;


    public ConcurrentArrayOffsetQueue(int capacity)
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
     * Calculate the distance of two values, assuming wrap around at JS_MAX_SAFE_INTEGER.
     * <p>
     * Safe up to a maximum distance of (JS_MAX_SAFE_INTEGER / 2).
     */
    private static long tailToOffsetDistance(long tailOffset, long offset)
    {
        long tailOpposite = (tailOffset + (JS_MAX_SAFE_INTEGER / 2)) % JS_MAX_SAFE_INTEGER;

        if (offset >= tailOffset)
        {
            return offset - tailOffset;
        }
        else if (tailOpposite < tailOffset && offset < tailOpposite)
        {
            return offset + (JS_MAX_SAFE_INTEGER - tailOffset);
        }

        return -1L; // optimization: exact distance doesn't matter here
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
            val newOffset = (((headElement != null) ? headElement.getOffset() : START_OFFSET) + 1) % JS_MAX_SAFE_INTEGER;

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
     * @param offset The most recent offset returned by this method or JS_MAX_SAFE_INTEGER to get the current offset.
     * @return List containing one Enqueueable with the offset set to the offset of the most recent element and the element set
     * to null in case of overflow, empty list if no newer elements exist, list of new Enqueueables since offset otherwise.
     */
    public List<Enqueueable<T>> pollSince(long offset)
    {
        val stateSnapshot = state.get();
        final int headIndex = getHead(stateSnapshot);

        // just return initial offset, user needs to poll again using that offset
        if (offset < 0 || offset >= JS_MAX_SAFE_INTEGER)
        {
            return createOverflowEnqueueable(stateSnapshot);
        }

        // pick oldest element and evaluate situation
        int i = getTail(stateSnapshot);
        val tailElement = queue.get(i);
        if (tailElement != null)
        {
            val tailOffset = tailElement.getOffset();
            val distanceToHead = (i < headIndex) ? headIndex - i : headIndex + (length - i);
            val tailToOffsetDistance = tailToOffsetDistance(tailOffset, offset + 1);

            final int targetCapacity;
            if (tailToOffsetDistance < 0 || tailToOffsetDistance > length)
            {
                // tail offset already greater than last polled offset: OVERFLOW
                // double check that head offset
                val previousHeadElement = queue.get((headIndex + length - 1) % length);
                if (previousHeadElement != null && (previousHeadElement.getOffset() - length - 1) > offset)
                {
                    return createOverflowEnqueueable(stateSnapshot);
                }

                // else: please try again later, need to wait until the queue is in sync with state
            }
            else
            {
                // advance to calculated index of first interesting element
                val distanceToFirstInterestingElement = Math.min(distanceToHead, (int) tailToOffsetDistance);

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
                            return createOverflowEnqueueable(state.get());
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
        }

        // no news, yet
        return Collections.emptyList();
    }


    /**
     * Create a singleton list of enqueueables that indicates an overflow and the next start offset to callers of pollSince().
     */
    private List<Enqueueable<T>> createOverflowEnqueueable(long stateSnapshot)
    {
        val headElement = queue.get((getHead(stateSnapshot) + length - 1) % length);
        val startOffset = (headElement != null) ? headElement.getOffset() : START_OFFSET;

        return Collections.singletonList(new SimpleEnqueueable<T>(startOffset, null));
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
