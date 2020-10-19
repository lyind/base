package net.talpidae.base.server.logging;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import lombok.extern.java.Log;
import lombok.val;


/**
 * Similar to RequestDumpingHandler but avoids DNS reverse lookups caused by InetSocketAddress.getHostName().
 */
@Log
public class RequestLoggingHandler implements HttpHandler
{
    private final HttpHandler next;

    public RequestLoggingHandler(final HttpHandler next)
    {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception
    {
        final StringBuilder sb = new StringBuilder();

        val forwardedFor = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_FOR);
        if (forwardedFor != null)
        {
            sb.append(forwardedFor);
        }
        else
        {
            sb.append(exchange.getSourceAddress().getHostString());
        }

        sb.append(' ');
        sb.append(exchange.getProtocol());
        sb.append(' ');
        sb.append(exchange.getRequestMethod());
        sb.append(' ');
        sb.append(exchange.getRequestURI());
        if (!"".equals(exchange.getQueryString()))
        {
            sb.append('?');
            sb.append(exchange.getQueryString());
        }

        exchange.addExchangeCompleteListener((completedExchange, nextListener) -> {

            sb.append(" -> status=");
            sb.append(completedExchange.getStatusCode());
            sb.append(" tx=");
            sb.append(completedExchange.getResponseBytesSent());

            nextListener.proceed();
            log.info(sb.toString());
        });

        next.handleRequest(exchange);
    }
}
