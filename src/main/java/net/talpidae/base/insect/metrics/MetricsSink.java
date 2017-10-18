package net.talpidae.base.insect.metrics;

import lombok.NonNull;


public interface MetricsSink
{
    /**
     * Form a metric tuple from the specified path and value and forward it to remote listeners.
     */
    void forward(@NonNull String path, double value);
}
