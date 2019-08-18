package net.talpidae.base.server.cors;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.server.ServerConfig;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;


/**
 * Filter that will immediately respond to CORS pre-flight requests (OPTIONS method) without
 * going through the JAX-RS stack or other Undertow filters.
 */
@Slf4j
public class CORSFilter implements HttpHandler
{
    private static final HttpString ACCESS_CONTROL_REQUEST_METHOD = new HttpString("access-control-request-method");

    private static final HttpString ACCESS_CONTROL_ALLOW_ORIGIN = new HttpString("access-control-allow-origin");

    private static final HttpString ACCESS_CONTROL_MAX_AGE = new HttpString("access-control-max-age");

    private static final HttpString ACCESS_CONTROL_ALLOW_CREDENTIALS = new HttpString("access-control-allow-credentials");

    private static final HttpString ACCESS_CONTROL_EXPOSE_HEADERS = new HttpString("access-control-expose-headers");

    private static final HttpString ACCESS_CONTROL_ALLOW_METHODS = new HttpString("access-control-allow-methods");

    private static final HttpString ACCESS_CONTROL_ALLOW_HEADERS = new HttpString("access-control-allow-headers");

    private static final HttpString X_FORWARDED_PROTO = new HttpString("x-forwarded-proto");

    private static final HttpString X_FORWARDED_HOST = new HttpString("x-forwarded-host");

    private static final HttpString X_FORWARDED_PORT = new HttpString("x-forwarded-port");

    private static final HttpString ORIGIN = new HttpString("origin");

    private static final HttpString VARY = new HttpString("vary");

    private static final String ALLOW_METHODS_DEFAULT = "GET,POST,PUT,PATCH,HEAD,OPTIONS,DELETE";

    private static final String MAX_AGE_DEFAULT = String.valueOf(86400);  // CORS TTL, 1d, capped by some browsers

    private static final String[] SAFELIST_EXPOSED_HEADERS = new String[]{
            "cache-control",
            "content-language",
            "content-type",
            "expires",
            "last-modified",
            "pragma"
    };

    private final HttpHandler next;

    private final ServerConfig serverConfig;

    private final Pattern pattern;

    private final String allowCredentials;

    private final String exposedHeaderNames;


    public CORSFilter(HttpHandler next, ServerConfig serverConfig)
    {
        this.next = next;
        this.serverConfig = serverConfig;

        this.pattern = Pattern.compile(serverConfig.getCorsOriginPattern());
        this.allowCredentials = String.valueOf(serverConfig.isCorsAllowCredentials());

        // do not expose "simple headers" (already exposed by spec)
        exposedHeaderNames = filterExposedHeaders(serverConfig.getCorsExposedHeaders());
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        if (exchange.isInIoThread())
        {
            exchange.dispatch(this);
            return;
        }

        val requestHeaders = exchange.getRequestHeaders();
        val origin = requestHeaders.getFirst(ORIGIN);
        if (origin != null)
        {
            if (pattern.matcher(origin).matches())
            {
                val responseHeaders = exchange.getResponseHeaders();

                responseHeaders.add(VARY, origin);
                ensureHeader(responseHeaders, ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                ensureHeader(responseHeaders, ACCESS_CONTROL_ALLOW_CREDENTIALS, allowCredentials);
                ensureHeader(responseHeaders, ACCESS_CONTROL_MAX_AGE, MAX_AGE_DEFAULT);

                // Is the request a CORS pre-flight request?
                if (Methods.OPTIONS.equals(exchange.getRequestMethod()) && requestHeaders.contains(ACCESS_CONTROL_REQUEST_METHOD))
                {
                    ensureHeader(responseHeaders, ACCESS_CONTROL_ALLOW_HEADERS, serverConfig.getCorsAllowHeaders());
                    ensureHeader(responseHeaders, ACCESS_CONTROL_ALLOW_METHODS, ALLOW_METHODS_DEFAULT);

                    // CORS pre-flight request, we may answer immediately
                    exchange.setStatusCode(200);
                    exchange.endExchange();
                }
                else
                {
                    ensureHeader(responseHeaders, ACCESS_CONTROL_EXPOSE_HEADERS, exposedHeaderNames);
                }

                // continue with other handlers, as normal
            }
            else
            {
                // reject (403 FORBIDDEN)
                log.warn("CORS request denied: " + extractUrl(exchange));
                exchange.setStatusCode(403);
                exchange.endExchange();
            }
        }

        // we do not let completed exchanges pass
        if (exchange.isComplete())
        {
            return;
        }

        next.handleRequest(exchange);
    }


    private static void ensureHeader(HeaderMap headers, HttpString key, String value)
    {
        if (!headers.contains(key))
        {
            headers.add(key, value);
        }
    }


    private static String extractUrl(HttpServerExchange exchange)
    {
        val headers = exchange.getRequestHeaders();

        final String proto;
        val originalProto = headers.getFirst(X_FORWARDED_PROTO);
        if (originalProto != null)
        {
            proto = originalProto;
        }
        else
        {
            proto = exchange.getRequestScheme();
        }

        final String port;
        val originalPort = headers.getFirst(X_FORWARDED_PORT);
        if (originalPort != null)
        {
            port = originalPort;
        }
        else
        {
            port = String.valueOf(exchange.getHostPort());
        }

        final String host;
        val originalHost = headers.getFirst(X_FORWARDED_HOST);
        if (originalHost != null)
        {
            host = originalHost;
        }
        else
        {
            host = exchange.getHostName();
        }

        val queryString = exchange.getQueryString();
        val query = queryString == null || queryString.isEmpty() ? "" : "?" + exchange.getQueryString();

        return proto + "://" + host + ":" + port + exchange.getRequestPath() + query;
    }


    private static String filterExposedHeaders(String configuredExposedHeaders)
    {
        val exposedConfig = configuredExposedHeaders.split(",");
        val alreadyExposed = Arrays.asList(SAFELIST_EXPOSED_HEADERS);
        val exposedBuilder = new StringBuilder();
        for (val exposedHeader : exposedConfig)
        {
            val header = exposedHeader.trim().toLowerCase(Locale.US);
            if (!alreadyExposed.contains(header))
            {
                if (exposedBuilder.length() > 0)
                {
                    exposedBuilder.append(',');
                }
                exposedBuilder.append(header);
            }
        }
        return exposedBuilder.toString();
    }
}
