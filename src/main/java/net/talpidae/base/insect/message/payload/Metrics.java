/*
 * Copyright (C) 2017  Jonas Zeiger <jonas.zeiger@talpidae.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.talpidae.base.insect.message.payload;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.util.performance.Metric;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.talpidae.base.util.protocol.BinaryProtocolHelper.extractString;
import static net.talpidae.base.util.protocol.BinaryProtocolHelper.putTruncatedUTF8;


@Slf4j
@Builder
public class Metrics extends Payload
{
    public static final int MAXIMUM_SERIALIZED_SIZE = 1472;

    public static final int TYPE_METRIC = 0x4;

    private static final int METRIC_COUNT_MAX = 255;

    private static final int STRING_SIZE_MAX = 255;


    @Getter
    @Builder.Default
    private final int type = TYPE_METRIC;    // 0x4: metrics

    @Getter
    private final List<Metric> metrics;  // metrics


    /**
     * Create a Metrics instance from binary data.
     * <p>
     * Basic binary layout is: tnGET/signup/statusCodeTTTTTTTTvvvvvvvv
     */
    static Metrics from(ByteBuffer buffer, int offset) throws IndexOutOfBoundsException, CharacterCodingException
    {
        val type = buffer.get(offset) & 0xFF;
        if (type != TYPE_METRIC)
        {
            return null;
        }

        val count = buffer.get(++offset) & 0xFF;
        val builder = Metrics.builder();
        builder.type(type);

        // extract metrics
        if (count > 0)
        {
            val metrics = new Metric[count];
            ++offset;
            for (int i = 0; i < count; ++i)
            {
                // extract one (path, value) pair
                val pathLength = buffer.get(++offset) & 0xFF;
                val tsOffset = offset + pathLength;
                val valueOffset = tsOffset + 8;
                metrics[i] = Metric.builder()
                        .path(extractString(buffer, offset, pathLength).intern())
                        .ts(buffer.getLong(tsOffset))
                        .value(buffer.getDouble(valueOffset))
                        .build();
            }

            builder.metrics(Arrays.asList(metrics));
        }
        else
        {
            builder.metrics(Collections.emptyList());
        }

        return builder.build();
    }


    @Override
    public void to(ByteBuffer buffer)
    {
        val begin = buffer.position();
        val limit = Math.min(buffer.limit(), buffer.position() + MAXIMUM_SERIALIZED_SIZE);

        // start writing dynamic fields behind static fields first
        // encode as many metrics as fit into one message, just drop the rest
        int offset = begin + 2;
        int count;
        for (count = 0; count < METRIC_COUNT_MAX; ++count)
        {
            val metric = metrics.get(count);
            val pathOffset = offset + 1;

            // optimistically write dynamic part (automatically limited by buffer's limit)
            val pathLength = putTruncatedUTF8(buffer, pathOffset, metric.getPath(), STRING_SIZE_MAX);

            val tsOffset = pathOffset + pathLength;
            val valueOffset = tsOffset + 8;
            val nextMetricOffset = valueOffset + 8;
            if (nextMetricOffset >= limit)
            {
                // doesn't fit anymore, sorry
                break;
            }

            // write static part
            buffer.put(offset, (byte) pathLength);
            buffer.putLong(tsOffset, metric.getTs());
            buffer.putDouble(valueOffset, metric.getValue());

            // advance
            offset = nextMetricOffset;
        }

        buffer.position(offset);  // include dynamic part
    }


    @Override
    public int getMaximumSize()
    {
        return MAXIMUM_SERIALIZED_SIZE;
    }


    @Override
    public String toString()
    {
        return Integer.toHexString(getType());
    }
}