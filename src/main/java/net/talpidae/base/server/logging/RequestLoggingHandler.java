package net.talpidae.base.server.logging;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import lombok.extern.java.Log;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * Similar to RequestDumpingHandler but avoids DNS reverse lookups caused by InetSocketAddress.getHostName().
 */
@Log
public class RequestLoggingHandler implements HttpHandler
{
    private static final HttpString USER_AGENT = new HttpString("user-agent");

    private static final HttpString AUTHORIZATION = new HttpString("authorization");

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

            val userAgent = completedExchange.getRequestHeaders().getFirst(USER_AGENT);
            if (userAgent != null)
            {
                sb.append(" ua=");
                sb.append(userAgent);
            }

            // extra a possible "sub" (subject) field from an "Authoriztation: Bearer" JWT token, if one exists
            val authorization = completedExchange.getRequestHeaders().getFirst(USER_AGENT);
            if (authorization != null)
            {
                // TODO: Also support logging Basic Auth username and other schemes/token formats
                val subject = extractJwtSub(authorization);
                if (subject != null)
                {
                    sb.append(" sub=");
                    sb.append(subject);
                }
            }

            nextListener.proceed();
            log.info(sb.toString());
        });

        next.handleRequest(exchange);
    }


    private static String extractJwtSub(String authorization)
    {
        if (authorization != null)
        {
            int begin = authorization.indexOf("Bearer");
            if (begin >= 0)
            {
                begin = authorization.indexOf('.', begin + 6);
                if (begin > 0)
                {
                    int end = authorization.indexOf('.', begin + 1);
                    if (end > begin)
                    {
                        try
                        {
                            String jwt = new String(Base64.getDecoder().decode(authorization.substring(begin + 1, end)), StandardCharsets.UTF_8);
                            begin = jwt.indexOf("\"sub\"");
                            if (begin > 0)
                            {
                                begin = jwt.indexOf(":", begin + 5);
                                if (begin > 0)
                                {
                                    begin = jwt.indexOf('\"', begin + 1);
                                    if (begin > 0)
                                    {
                                        end = jwt.indexOf('\"', begin + 1);
                                        if (end > begin)
                                        {
                                            return jwt.substring(begin + 1, end);
                                        }
                                    }
                                }
                            }
                        }
                        catch (RuntimeException e)
                        {
                            // ignore, just return null
                        }
                    }
                }
            }
        }

        return null;
    }
}
