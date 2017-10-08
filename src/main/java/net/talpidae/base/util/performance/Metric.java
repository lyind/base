package net.talpidae.base.util.performance;

import lombok.Builder;
import lombok.Value;


@Value
@Builder
public class Metric
{
    /**
     * Metric path, example: "GET/signup/statusCode".
     */
    private final String path;

    /**
     * Timestamp of measurement in milliseconds as returned from System.currentTimeMillies().
     */
    private final long ts;

    private final double value;
}