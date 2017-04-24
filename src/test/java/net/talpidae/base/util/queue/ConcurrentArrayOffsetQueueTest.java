package net.talpidae.base.util.queue;


import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class ConcurrentArrayOffsetQueueTest
{
    @Test
    public void addAndPollSinceTest()
    {
        int[] data = new int[]{42, 43, 12, 5, 8};

        ConcurrentArrayOffsetQueue<Integer> queue = new ConcurrentArrayOffsetQueue<>(data.length);

        List<Enqueueable<Integer>> initialOffset = queue.pollSince(Long.MAX_VALUE);

        // fetch initial offset
        long offset = initialOffset.get(0).getOffset();
        assertEquals(null, initialOffset.get(0).getElement());

        // create events
        for (int d : data)
        {
            queue.add(d);
        }

        // poll for changes since initial offset
        List<Enqueueable<Integer>> news = queue.pollSince(offset);
        assertEquals(data.length, news.size());
        for (int i = 0; i < data.length; ++i)
        {
            Enqueueable<Integer> newsItem = news.get(i);
            assertEquals(offset + i + 1, newsItem.getOffset());
            assertEquals(data[i], newsItem.getElement().intValue());
        }
    }


    @Test
    public void concurrentAddAndPollSinceTest()
    {
        final int testThreadCount = 2;
        final int testElementCount = 1000000;
        final int[] data = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        final ConcurrentArrayOffsetQueue<Integer> queue = new ConcurrentArrayOffsetQueue<>(testElementCount / 20);

        final AtomicInteger startLatch = new AtomicInteger(0);
        final AtomicInteger consumeLatch = new AtomicInteger(0);

        for (int threadIndex = 0; threadIndex < testThreadCount; ++threadIndex)
        {
            new Thread(() ->
            {
                int receivedCount = 0;

                List<Enqueueable<Integer>> initialOffset = queue.pollSince(Long.MAX_VALUE);

                // fetch initial offset
                long offset = initialOffset.get(0).getOffset();
                assertEquals(null, initialOffset.get(0).getElement());

                startLatch.incrementAndGet();

                while (receivedCount < testElementCount)
                {
                    // poll for changes since initial offset
                    List<Enqueueable<Integer>> news = queue.pollSince(offset);

                    for (int i = 0; i < news.size(); ++i)
                    {
                        Enqueueable<Integer> newsItem = news.get(i);
                        assertEquals("offset: " + offset + ", received: " + receivedCount, offset + 1, newsItem.getOffset());
                        assertEquals("offset: " + offset + ", received: " + receivedCount, data[receivedCount % data.length], newsItem.getElement().intValue());

                        offset = newsItem.getOffset();
                        ++receivedCount;
                    }

                    consumeLatch.addAndGet(news.size());
                    Thread.yield();
                }

                startLatch.decrementAndGet();
            }).start();
        }

        // wait until the threads started and picked up their start offset (not necessary in real life)
        while (startLatch.get() < testThreadCount)
            Thread.yield();

        // create events, waiting for consumers after filling the queue,
        // else we'd just get them reporting queue overflows
        for (int i = 0; i < testElementCount; )
        {
            for (int endBlock = i + (testElementCount / 20); i < endBlock; i += data.length)
            {
                for (int d : data)
                {
                    queue.add(d);
                }
            }

            final long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (consumeLatch.get() < i * testThreadCount)
            {
                if (System.nanoTime() >= timeout)
                    fail("timeout waiting for consumers");

                Thread.yield();
            }
        }

        // wait for all threads to finish
        while (startLatch.get() > 0)
            Thread.yield();
    }
}
