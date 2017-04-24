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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.val;
import org.assertj.core.util.Strings;
import org.jdbi.v3.core.Jdbi;

import javax.sql.DataSource;
import java.util.Optional;


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
    }


    @Provides
    @Singleton
    public Jdbi jdbiProvider(Optional<ManagedSchema> optionalManagedSchema)
    {
        return optionalManagedSchema.map(schema -> Jdbi.create(schema.migrate()).installPlugins())
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
    public Optional<DataSource> hikariDataSourceProvider(Optional<HikariConfig> hikariConfig)
    {
        return hikariConfig.map(HikariDataSource::new);
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