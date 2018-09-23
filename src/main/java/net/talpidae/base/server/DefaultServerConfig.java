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

import net.talpidae.base.resource.RestApplication;
import net.talpidae.base.util.Application;
import net.talpidae.base.util.BaseArguments;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;

import io.undertow.server.HandlerWrapper;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

import static net.talpidae.base.util.injector.BindingInspector.getLinkedClassInterfaces;


@Singleton
public class DefaultServerConfig implements ServerConfig
{
    private static final int PORT_MAX = 65535;

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
    private boolean isLoggingFeatureEnabled = true;

    @Setter
    @Getter
    private Class<? extends WebSocketEndpoint> webSocketEndPoint = null;

    @Setter
    @Getter
    private HandlerWrapper rootHandlerWrapper;

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


    @Inject
    public DefaultServerConfig(ServerConfig serverConfig, BaseArguments baseArguments, Injector injector)
    {
        val parser = baseArguments.getOptionParser();
        val portOption = parser.accepts("server.port").withRequiredArg().ofType(Integer.class).defaultsTo(0);
        val hostOption = parser.accepts("server.host").withRequiredArg().ofType(String.class).defaultsTo("127.0.0.1");
        val disableHttp2Option = parser.accepts("server.disableHttp2").withRequiredArg().ofType(Boolean.class).defaultsTo(false);
        val keyStorePathOption = parser.accepts("server.keyStore").withRequiredArg().ofType(String.class).defaultsTo("");
        val keyStoreTypeOption = parser.accepts("server.keyStoreType").withRequiredArg().ofType(String.class).defaultsTo("PKCS12");
        val keyStorePasswordOption = parser.accepts("server.keyStorePassword").withRequiredArg().ofType(String.class).defaultsTo("");
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

        // validate the specified host to fail early
        InetAddresses.forString(this.host);

        this.isRestEnabled = getLinkedClassInterfaces(injector, Application.class)
                .contains(RestApplication.class);
    }
}
