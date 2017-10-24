package net.talpidae.base.insect.metrics;

import net.talpidae.base.insect.Slave;
import net.talpidae.base.insect.config.SlaveSettings;
import net.talpidae.base.util.performance.Metric;
import net.talpidae.base.util.thread.GeneralScheduler;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
@Singleton
public class QueuedMetricsSink implements MetricsSink
{
    private static final long MAXIMUM_ENQUEUE_DELAY_NANOS = TimeUnit.SECONDS.toNanos(4);

    private static final int MAXIMUM_QUEUE_LENGTH = 2048;

    /**
     * How many metric elements to leave in the metricQueue without immediately trying to send again.
     */
    private static final int MAXIMUM_DELAYED_ITEM_COUNT = 30;

    private final Slave slave;

    private final ArrayDeque<Metric> metricQueue = new ArrayDeque<>(1024);

    private final String pathPrefix;

    private long lastEnqueueNanos = 0L;


    @Inject
    public QueuedMetricsSink(Slave slave, SlaveSettings slaveSettings, GeneralScheduler scheduler)
    {
        this.slave = slave;
        this.pathPrefix = "/" + slaveSettings.getName();

        scheduler.scheduleWithFixedDelay(this::sendAllMetrics, MAXIMUM_ENQUEUE_DELAY_NANOS, MAXIMUM_ENQUEUE_DELAY_NANOS, TimeUnit.NANOSECONDS);
    }


    /**
     * Form a metric tuple from the specified path and value and enqueue it for forwarding.
     */
    @Override
    public void forward(@NonNull String path, long timestampMillies, double value)
    {
        val absolutePath = pathPrefix + path;
        val metric = Metric.builder()
                .path(absolutePath.replace("//", "/"))
                .ts(timestampMillies)
                .value(value)
                .build();

        synchronized (metricQueue)
        {
            if (metricQueue.size() < MAXIMUM_QUEUE_LENGTH)
            {
                metricQueue.addLast(metric);
            }
        }
    }


    private int getQueueLength()
    {
        synchronized (metricQueue)
        {
            return metricQueue.size();
        }
    }


    private boolean isQueueEmpty()
    {
        synchronized (metricQueue)
        {
            return metricQueue.isEmpty();
        }
    }


    private void sendAllMetrics()
    {
        val now = System.nanoTime();
        if (lastEnqueueNanos < now - MAXIMUM_ENQUEUE_DELAY_NANOS
                && !isQueueEmpty())
        {
            lastEnqueueNanos = now;

            try
            {
                do
                {
                    synchronized (metricQueue)
                    {
                        slave.forwardMetrics(metricQueue);
                    }
                }
                while (getQueueLength() >= MAXIMUM_DELAYED_ITEM_COUNT);
            }
            catch (Throwable t)
            {
                log.error("failed to forward metrics", t);
            }
        }
    }
}
