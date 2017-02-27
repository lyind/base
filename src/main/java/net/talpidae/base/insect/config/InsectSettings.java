package net.talpidae.base.insect.config;

import java.net.InetSocketAddress;
import java.util.Set;


public interface InsectSettings
{
    /**
     * InetSocketAddress to bind to.
     */
    InetSocketAddress getBindAddress();

    /**
     * Remote servers that are authorized to update mappings they do not own themselves
     * and are informed about our services.
     */
    Set<InetSocketAddress> getRemotes();
}
