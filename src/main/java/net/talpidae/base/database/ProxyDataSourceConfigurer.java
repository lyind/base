package net.talpidae.base.database;

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;


public interface ProxyDataSourceConfigurer
{
    void configure(ProxyDataSourceBuilder proxyDataSourceBuilder);
}
