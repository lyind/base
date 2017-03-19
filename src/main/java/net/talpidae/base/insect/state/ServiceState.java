package net.talpidae.base.insect.state;

import java.net.InetSocketAddress;


public interface ServiceState
{
    long getTimestamp();

    InetSocketAddress getSocketAddress();
}
