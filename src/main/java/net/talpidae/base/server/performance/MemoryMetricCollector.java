package net.talpidae.base.server.performance;


import net.talpidae.base.insect.metrics.MetricsSink;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import lombok.extern.slf4j.Slf4j;
import lombok.val;


/**
 * Collect heap and non-heap memory statistics and forward them using the specified MetricsSink instance.
 */
@Slf4j
public class MemoryMetricCollector implements Runnable
{
    private final MetricsSink metricsSink;

    private final MemoryMXBean memoryMXBean;


    public MemoryMetricCollector(MetricsSink metricsSink)
    {
        this.metricsSink = metricsSink;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    }


    @Override
    public void run()
    {
        try
        {
            val ts = System.currentTimeMillis();
            val heapCommitted = ((double) memoryMXBean.getHeapMemoryUsage().getCommitted()) / 1024 / 1024;  // MBytes
            val nonHeapCommitted = ((double) memoryMXBean.getNonHeapMemoryUsage().getCommitted()) / 1024 / 1024;

            metricsSink.forward("/heapCommitted", ts, heapCommitted);
            metricsSink.forward("/nonHeapCommitted", ts, nonHeapCommitted);
        }
        catch (Throwable t)
        {
            log.error("memory metrics collection failed", t);
        }
    }
}
