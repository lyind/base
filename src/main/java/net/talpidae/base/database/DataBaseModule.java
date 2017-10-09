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

package net.talpidae.base.database;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.talpidae.base.util.configuration.Configurer;
import net.talpidae.base.util.lifecycle.CloseOnShutdown;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import java.util.Optional;

import javax.sql.DataSource;

import lombok.val;


/**
 * Provides base database functionality (including schema migration).
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DataBaseModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        OptionalBinder.newOptionalBinder(binder(), DefaultDataBaseConfig.class);

        // optional configurer for Jdbi
        OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<Configurer<Jdbi>>() {});

        // optional configurer for ProxyDataSourceBuilder
        OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<Configurer<ProxyDataSourceBuilder>>() {});
    }


    @Provides
    @Singleton
    public Jdbi jdbiProvider(Optional<ManagedSchema> optionalManagedSchema,
                             Optional<Configurer<Jdbi>> optionalJdbiConfigurer,
                             Optional<Configurer<ProxyDataSourceBuilder>> optionalProxyDataSourceConfigurer)
    {
        return optionalManagedSchema
                .map(ManagedSchema::migrate)
                .map(dataSource ->
                        // dataSource proxy requested?
                        optionalProxyDataSourceConfigurer
                                .map(configurer ->
                                {
                                    val proxyBuilder = ProxyDataSourceBuilder.create(dataSource);

                                    configurer.configure(proxyBuilder);

                                    return (DataSource) proxyBuilder.build();
                                })
                                .orElse(dataSource))
                .map(Jdbi::create)
                .map(jdbi -> jdbi.installPlugin(new SqlObjectPlugin()))
                .map(jdbi -> optionalJdbiConfigurer.map(configurer ->
                {
                    configurer.configure(jdbi);
                    return jdbi;
                })
                        .orElse(jdbi))
                .orElseThrow(() -> new IllegalArgumentException("Can't initialize JDBI, no DefaultDataBaseConfig provided."));
    }


    @Provides
    @Singleton
    public Optional<ManagedSchema> optionalManagedSchemaProvider(Optional<DataSource> optionalDataSource)
    {
        return optionalDataSource.map(FlywayManagedSchema::new);
    }


    @Provides
    @Singleton
    public Optional<DataSource> hikariDataSourceProvider(Optional<HikariConfig> hikariConfig, CloseOnShutdown closeOnShutdown)
    {
        return hikariConfig.map(HikariDataSource::new).map(closeOnShutdown::add);
    }


    @Provides
    @Singleton
    public Optional<HikariConfig> hikariConfigProvider(OverridableDataBaseConfig dataBaseConfig)
    {
        if (!dataBaseConfig.isDatabaseEnabled())
        {
            return Optional.empty();
        }

        final HikariConfig config = new HikariConfig();

        if (!Strings.isNullOrEmpty(dataBaseConfig.getPoolName()))
            config.setPoolName(dataBaseConfig.getPoolName());

        if (!Strings.isNullOrEmpty(dataBaseConfig.getDriverClassName()))
            config.setDriverClassName(dataBaseConfig.getDriverClassName());

        config.setJdbcUrl(dataBaseConfig.getJdbcUrl());
        config.setConnectionTestQuery(dataBaseConfig.getConnectionTestQuery());
        config.setMaxLifetime(dataBaseConfig.getMaxLifetime()); // 72s
        config.setIdleTimeout(dataBaseConfig.getIdleTimeout()); // 45s Sec
        config.setMaximumPoolSize(dataBaseConfig.getMaximumPoolSize());

        config.setUsername(dataBaseConfig.getUserName());
        config.setPassword(dataBaseConfig.getPassword());

        for (val propertyEntry : dataBaseConfig.getDataSourceProperties().entrySet())
        {
            config.addDataSourceProperty(propertyEntry.getKey(), propertyEntry.getValue());
        }

        return Optional.of(config);
    }
}
