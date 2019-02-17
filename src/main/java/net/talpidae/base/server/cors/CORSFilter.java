package net.talpidae.base.server.cors;

import com.stijndewitt.undertow.cors.Filter;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.regex.Pattern;


/**
 * Filter that will immediately respond to CORS pre-flight requests (OPTIONS method) without
 * going through the JAX-RS stack or other Undertow filters.
 */
@Slf4j
public class CORSFilter extends Filter
{
    private static final HttpString ACCESS_CONTROL_REQUEST_METHOD = new HttpString("access-control-request-method");

    private final HttpHandler next;

    private transient Pattern pattern;

    public CORSFilter(HttpHandler next)
    {
        super(next);
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        if (exchange.isInIoThread())
        {
            exchange.dispatch(this);
            return;
        }

        if (pattern == null)
        {
            pattern = Pattern.compile(getUrlPattern());
        }

        val origin = origin(exchange);
        if (origin != null)
        {
            val url = url(exchange);
            if (pattern.matcher(url).matches())
            {
                val allowed = applyPolicy(exchange, origin);
                if (allowed)
                {
                    addHeader(exchange, "Vary", origin);

                    if (isPreFlight(exchange))
                    {
                        // CORS pre-flight request, we may answer immediately
                        // reject FORBIDDEN
                        exchange.setStatusCode(200);
                        exchange.endExchange();
                        return;
                    }

                    // continue with other handlers, as normal
                }
                else
                {
                    // reject FORBIDDEN
                    log.warn("CORS request rejected: " + url);
                    exchange.setStatusCode(403);
                    exchange.endExchange();
                    return;
                }
            }
        }

        next.handleRequest(exchange);
    }

    /**
     * Is the request a CORS pre-flight request?
     */
    protected static boolean isPreFlight(HttpServerExchange exchange)
    {
        return Methods.OPTIONS.equals(exchange.getRequestMethod())
                && exchange.getResponseHeaders().get(ACCESS_CONTROL_REQUEST_METHOD) != null;
    }
}
