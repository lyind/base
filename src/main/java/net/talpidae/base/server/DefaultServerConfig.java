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

package net.talpidae.base.server;

import com.google.common.net.InetAddresses;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.undertow.server.HandlerWrapper;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import net.talpidae.base.resource.DefaultRestApplication;
import net.talpidae.base.util.BaseArguments;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import java.util.concurrent.TimeUnit;


@Singleton
public class DefaultServerConfig implements ServerConfig
{
    private static final String DEFAULT_CORS_ALLOW_HEADERS = "authorization,content-type,link,x-total-count,range,content-length,content-encoding";

    private static final String DEFAULT_CORS_EXPOSED_HEADERS = "cache-control,etag,x-total-count,x-item-count,server,link,content-range,content-language,content-type,expires,last-modified,pragma,content-length,accept-ranges";

    private static final String[] DEFAULT_CIPHER_SUITES = new String[]{
            "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_PSK_WITH_AES_128_CBC_SHA",
            "TLS_PSK_WITH_AES_256_CBC_SHA",
            "TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA",
            "TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384"
    };

    private static final int PORT_MAX = 65535;

    private static final int DEFAULT_NO_REQUEST_TIMEOUT_MS = (int) TimeUnit.MINUTES.toMillis(5);

    private static final int DEFAULT_IDLE_TIMEOUT_MS = (int) TimeUnit.MINUTES.toMillis(6);

    @Getter
    private final boolean isRestEnabled;

    @Setter
    @Getter
    private int port;

    @Setter
    @Getter
    private String host;

    @Setter
    @Getter
    private Class<? extends HttpServlet> customHttpServletClass = null;

    @Setter
    @Getter
    private boolean isLoggingFeatureEnabled;

    @Setter
    @Getter
    private Class<? extends WebSocketEndpoint> webSocketEndPoint = null;

    @Setter
    @Getter
    private HandlerWrapper rootHandlerWrapper;

    @Setter
    @Getter
    private String corsOriginPattern;

    @Setter
    @Getter
    private String corsAllowHeaders;

    @Setter
    @Getter
    private String corsExposedHeaders;

    @Setter
    @Getter
    private boolean corsAllowCredentials;

    @Setter
    @Getter
    private boolean isBehindProxy = true;

    @Setter
    @Getter
    private boolean isDisableHttp2 = false;

    @Setter
    @Getter
    private String keyStorePath;

    @Setter
    @Getter
    private String keyStoreType;  // assume key-store in PKCS#12 (".p12" or ".pfx") files by default

    @Setter
    @Getter
    private String keyStorePassword;  // TODO Maybe it would be wise to read this from STDIN instead?

    @Setter
    @Getter
    private String[] cipherSuites;

    /** SSL SessionCache size limit (0 means unlimited). */
    @Setter
    @Getter
    private int sessionCacheSize;

    /** SSL session timeout (0 means unlimited, in seconds). */
    @Setter
    @Getter
    private int sessionTimeout;

    /** No request timeout (max. time between requests in MS). */
    @Setter
    @Getter
    private int noRequestTimeout;

    /** Connection idle timeout in MS. */
    @Setter
    @Getter
    private int idleTimeout;

    @Inject
    public DefaultServerConfig(BaseArguments baseArguments, Injector injector)
    {
        val parser = baseArguments.getOptionParser();
        val portOption = parser.accepts("server.port").withRequiredArg().ofType(Integer.class).defaultsTo(0);
        val hostOption = parser.accepts("server.host").withRequiredArg().ofType(String.class).defaultsTo("127.0.0.1");
        val loggingOption = parser.accepts("server.logging").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
        val disableHttp2Option = parser.accepts("server.disableHttp2").withRequiredArg().ofType(Boolean.class).defaultsTo(false);
        val keyStorePathOption = parser.accepts("server.keyStore").withRequiredArg().ofType(String.class).defaultsTo("");
        val keyStoreTypeOption = parser.accepts("server.keyStoreType").withRequiredArg().ofType(String.class).defaultsTo("PKCS12");
        val keyStorePasswordOption = parser.accepts("server.keyStorePassword").withRequiredArg().ofType(String.class).defaultsTo("");
        val corsOriginPatternOption = parser.accepts("server.cors.originPattern").withRequiredArg().ofType(String.class);
        val corsAllowHeadersOption = parser.accepts("server.cors.allowHeaders").withRequiredArg().ofType(String.class).defaultsTo(DEFAULT_CORS_ALLOW_HEADERS);
        val corsExposedHeadersOption = parser.accepts("server.cors.exposedHeaders").withRequiredArg().ofType(String.class).defaultsTo(DEFAULT_CORS_EXPOSED_HEADERS);
        val corsAllowCredentialsOption = parser.accepts("server.cors.allowCredentials").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
        val cipherSuitesOption = parser.accepts("server.cipherSuites").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',').defaultsTo(DEFAULT_CIPHER_SUITES);
        val sessionCacheSizeOption = parser.accepts("server.sessionCacheSize").withRequiredArg().ofType(Integer.class).defaultsTo(0);
        val sessionTimeoutOption = parser.accepts("server.sessionTimeout").withRequiredArg().ofType(Integer.class).defaultsTo(86400);  // default 1d
        val noRequestTimeoutOption = parser.accepts("server.noRequestTimeout").withRequiredArg().ofType(Integer.class).defaultsTo(DEFAULT_NO_REQUEST_TIMEOUT_MS);  // default 5min
        val idleTimeoutOption = parser.accepts("server.idleTimeout").withRequiredArg().ofType(Integer.class).defaultsTo(DEFAULT_IDLE_TIMEOUT_MS);  // default 6min

        val options = baseArguments.parse();

        this.port = options.valueOf(portOption);
        if (port < 0 || port > PORT_MAX)
        {
            throw new IllegalArgumentException("invalid port specified: " + port);
        }

        this.host = options.valueOf(hostOption).intern();
        this.isDisableHttp2 = options.valueOf(disableHttp2Option);
        this.keyStorePath = options.valueOf(keyStorePathOption);
        this.keyStoreType = options.valueOf(keyStoreTypeOption);
        this.keyStorePassword = options.valueOf(keyStorePasswordOption);
        this.isLoggingFeatureEnabled = options.valueOf(loggingOption);
        this.corsOriginPattern = options.valueOf(corsOriginPatternOption);
        this.corsAllowHeaders = options.valueOf(corsAllowHeadersOption);
        this.corsExposedHeaders = options.valueOf(corsExposedHeadersOption);
        this.corsAllowCredentials = options.valueOf(corsAllowCredentialsOption);
        this.cipherSuites = options.valuesOf(cipherSuitesOption).toArray(new String[0]);
        this.sessionCacheSize = options.valueOf(sessionCacheSizeOption);
        this.sessionTimeout = options.valueOf(sessionTimeoutOption);
        this.noRequestTimeout = options.valueOf(noRequestTimeoutOption);
        this.idleTimeout = options.valueOf(idleTimeoutOption);

        // validate the specified host to fail early
        InetAddresses.forString(this.host);

        // REST application bound? (RestModule loaded?)
        this.isRestEnabled = injector.getExistingBinding(Key.get(DefaultRestApplication.class)) != null;
    }
}
