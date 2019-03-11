package net.talpidae.base.server.performance;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import lombok.Getter;
import lombok.val;
import net.talpidae.base.insect.metrics.MetricsSink;

import java.util.concurrent.TimeUnit;


public class MetricsHandler implements HttpHandler
{
    private static final AttachmentKey<ExchangeMetric> EXCHANGE_METRIC = AttachmentKey.create(ExchangeMetric.class);

    private final HttpHandler next;

    private final MetricsSink metricsSink;


    public MetricsHandler(HttpHandler next, MetricsSink metricsSink)
    {
        this.next = next;
        this.metricsSink = metricsSink;
    }


    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        if (!exchange.isComplete())
        {
            exchange.putAttachment(EXCHANGE_METRIC, new ExchangeMetric(exchange.getRelativePath()));

            exchange.addExchangeCompleteListener((completedExchange, nextListener) ->
            {
                final Runnable finishRequest = () ->
                {
                    val exchangeMetric = completedExchange.getAttachment(EXCHANGE_METRIC);

                    exchangeMetric.complete(completedExchange);
                    forwardRequestMetric(exchangeMetric);

                    nextListener.proceed();
                };

                if (completedExchange.isInIoThread())
                {
                    completedExchange.dispatch(finishRequest);
                }
                else
                {
                    finishRequest.run();
                }
            });
        }
        next.handleRequest(exchange);
    }

    
    private void forwardRequestMetric(ExchangeMetric exchangeMetric)
    {
        val ts = exchangeMetric.getTimestampMillies();
        metricsSink.forward(exchangeMetric.getPath() + "/duration", ts, exchangeMetric.getDuration());
        metricsSink.forward(exchangeMetric.getPath() + "/status", ts, exchangeMetric.getStatusCode());
    }


    private static class ExchangeMetric
    {
        private static final double NANOSECONDS_TO_FRACTIONAL_SECONDS_MULTIPLIER = 1.0 / TimeUnit.SECONDS.toNanos(1);

        private final long startNanos;

        @Getter
        private final long timestampMillies;

        @Getter
        private final String path;

        @Getter
        private double duration;

        @Getter
        private double statusCode;


        private ExchangeMetric(String path)
        {
            this.timestampMillies = System.currentTimeMillis();
            this.startNanos = System.nanoTime();
            this.path = path;
        }


        private void complete(HttpServerExchange exchange)
        {
            val endNanos = System.nanoTime();

            duration = (double) (endNanos - startNanos) * NANOSECONDS_TO_FRACTIONAL_SECONDS_MULTIPLIER;
            statusCode = exchange.getStatusCode();
        }
    }
}