package net.talpidae.base.insect.metrics;

import net.talpidae.base.insect.Slave;
import net.talpidae.base.insect.config.SlaveSettings;
import net.talpidae.base.util.performance.Metric;
import net.talpidae.base.util.thread.GeneralScheduler;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.NonNull;
import lombok.val;


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

    private final SlaveSettings slaveSettings;

    private long lastEnqueueNanos = 0L;


    @Inject
    public QueuedMetricsSink(Optional<Slave> slave, Optional<SlaveSettings> slaveSettings, GeneralScheduler scheduler)
    {
        this.slave = slave.orElse(null);
        if (slave.isPresent())
        {
            scheduler.scheduleWithFixedDelay(this::sendAllMetrics, MAXIMUM_ENQUEUE_DELAY_NANOS, MAXIMUM_ENQUEUE_DELAY_NANOS, TimeUnit.NANOSECONDS);
        }

        this.slaveSettings = slaveSettings.orElse(null);
    }


    /**
     * Form a metric tuple from the specified path and value and enqueue it for forwarding.
     */
    public void forward(@NonNull String path, double value)
    {
        if (slave != null)
        {
            val now = System.currentTimeMillis();
            val absolutePath = "/" + slaveSettings.getName() + "/" + path;
            val metric = Metric.builder()
                    .path(absolutePath.replace("//", "/"))
                    .ts(now)
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

            do
            {
                synchronized (metricQueue)
                {
                    slave.forwardMetrics(metricQueue);
                }
            }
            while (getQueueLength() >= MAXIMUM_DELAYED_ITEM_COUNT);
        }
    }
}
