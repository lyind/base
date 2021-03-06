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

import io.undertow.server.HandlerWrapper;

import javax.servlet.http.HttpServlet;


public interface ServerConfig
{
    int getPort();

    void setPort(int port);

    String getHost();

    void setHost(String host);

    boolean isRestEnabled();

    Class<? extends HttpServlet> getCustomHttpServletClass();

    void setCustomHttpServletClass(Class<? extends HttpServlet> customHttpServletClass);

    boolean isLoggingFeatureEnabled();

    void setLoggingFeatureEnabled(boolean isLoggingFeatureEnabled);

    String getCorsOriginPattern();

    void setCorsOriginPattern(String corsOriginPattern);

    String getCorsAllowHeaders();

    void setCorsAllowHeaders(String corsAllowHeaders);

    String getCorsExposedHeaders();

    void setCorsExposedHeaders(String corsExposedHeaders);

    boolean isCorsAllowCredentials();

    void setCorsAllowCredentials(boolean isCorsAllowCredentials);

    HandlerWrapper getRootHandlerWrapper();

    void setRootHandlerWrapper(HandlerWrapper rootHandlerWrapper);

    boolean isBehindProxy();

    void setBehindProxy(boolean isBehindProxy);

    boolean isDisableHttp2();

    void setDisableHttp2(boolean isDisableHttp2);

    String getKeyStorePath();

    void setKeyStorePath(String keyStorePath);

    String getKeyStoreType();

    void setKeyStoreType(String keyStoreType);

    String getKeyStorePassword();

    void setKeyStorePassword(String keyStorePassword);

    String[] getCipherSuites();

    void setCipherSuites(String[] cipherSuites);

    int getSessionCacheSize();

    void setSessionCacheSize(int sessionCacheSize);

    int getSessionTimeout();

    void setSessionTimeout(int sessionTimeout);

    int getNoRequestTimeout();

    void setNoRequestTimeout(int noRequestTimeout);

    int getIdleTimeout();

    void setIdleTimeout(int idleTimeout);
}
