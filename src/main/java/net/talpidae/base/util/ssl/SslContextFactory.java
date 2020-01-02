/*
 * Copyright 2017 Jonas Zeiger <jonas.zeiger@talpidae.net>
 *
 * This is a copied and slightly modified version, original:
 *
 * author: Harald Wellmann
 * location: https://github.com/ops4j/org.ops4j.pax.web/blob/web-5.0.0.M1/pax-web-undertow/src/main/java/org/ops4j/pax/web/undertow/ssl/SslContextFactory.java
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.talpidae.base.util.ssl;

import net.talpidae.base.server.ServerConfig;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;


public class SslContextFactory
{
    private final static TrustManager[] TRUST_ALL_CERTS = new X509TrustManager[]{new DummyTrustManager()};

    private final ServerConfig serverConfig;


    public SslContextFactory(ServerConfig serverConfig)
    {
        // disable TLSv1.3+ for now (avoid bugs in server software)
        java.lang.System.setProperty("jdk.tls.server.protocols", "TLSv1,TLSv1.1,TLSv1.2");

        this.serverConfig = serverConfig;
    }


    public SSLContext createSslContext() throws IOException
    {
        String keyStoreName = serverConfig.getKeyStorePath();
        String keyStoreType = serverConfig.getKeyStoreType();
        String keyStorePassword = serverConfig.getKeyStorePassword();

        final KeyStore keyStore = loadKeyStore(keyStoreName, keyStoreType, keyStorePassword);
        final KeyManager[] keyManagers = buildKeyManagers(keyStore, keyStorePassword.toCharArray());
        final TrustManager[] trustManagers = buildTrustManagers(null);

        SSLContext sslContext;
        try
        {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            final SSLParameters params = sslContext.getDefaultSSLParameters();
            params.setCipherSuites(serverConfig.getCipherSuites());
            sslContext.getServerSessionContext().setSessionCacheSize(serverConfig.getSessionCacheSize());
            sslContext.getServerSessionContext().setSessionTimeout(serverConfig.getSessionTimeout());
        }
        catch (NoSuchAlgorithmException | KeyManagementException exc)
        {
            throw new IOException("Unable to create and initialise the SSLContext", exc);
        }

        return sslContext;
    }

    private static KeyStore loadKeyStore(final String location, String type, String storePassword)
            throws IOException
    {
        String url = location;
        if (url.indexOf(':') == -1)
        {
            url = "file:" + location;
        }

        final InputStream stream = new URL(url).openStream();
        try
        {
            KeyStore loadedKeystore = KeyStore.getInstance(type);
            loadedKeystore.load(stream, storePassword.toCharArray());
            return loadedKeystore;
        }
        catch (KeyStoreException | NoSuchAlgorithmException | CertificateException exc)
        {
            throw new IOException(String.format("Unable to load KeyStore %s", location), exc);
        }
        finally
        {
            stream.close();
        }
    }

    private static TrustManager[] buildTrustManagers(final KeyStore trustStore) throws IOException
    {
        if (trustStore != null)
        {
            try
            {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory
                        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                return trustManagerFactory.getTrustManagers();
            }
            catch (NoSuchAlgorithmException | KeyStoreException exc)
            {
                throw new IOException("Unable to initialise TrustManager[]", exc);
            }
        }
        else
        {
            return TRUST_ALL_CERTS;
        }
    }

    private static KeyManager[] buildKeyManagers(final KeyStore keyStore, char[] storePassword)
            throws IOException
    {
        KeyManager[] keyManagers;
        try
        {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, storePassword);
            keyManagers = keyManagerFactory.getKeyManagers();
        }
        catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException exc)
        {
            throw new IOException("Unable to initialise KeyManager[]", exc);
        }
        return keyManagers;
    }
}
