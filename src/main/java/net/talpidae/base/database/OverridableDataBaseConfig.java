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

import net.talpidae.base.util.BaseArguments;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.val;


/**
 * Allow overriding DefaultDataBaseConfig via command line options.
 */
@Singleton
@Getter
public class OverridableDataBaseConfig implements DataBaseConfig
{
    private final int maximumPoolSize;

    private final String jdbcUrl;

    private final String userName;

    private final String password;

    private final String poolName;

    private final String driverClassName;

    private final String connectionTestQuery;

    private final int maxLifetime;

    private final int idleTimeout;

    private final Map<String, String> dataSourceProperties = new HashMap<>();

    /*
     * Is database functionality enabled?
     */
    private final boolean databaseEnabled;


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Inject
    public OverridableDataBaseConfig(Optional<DefaultDataBaseConfig> optionalDefaults, BaseArguments baseArguments)
    {
        this.databaseEnabled = optionalDefaults.isPresent();
        val defaults = optionalDefaults.orElseGet(NoDataBaseByDefaultConfig::new);

        val parser = baseArguments.getOptionParser();
        val maximumPoolSizeOption = parser.accepts("db.maximumPoolSize").withRequiredArg().ofType(Integer.class).defaultsTo(defaults.getMaximumPoolSize());
        val jdbcUrlOption = parser.accepts("db.jdbc.url").withRequiredArg().defaultsTo(defaults.getJdbcUrl());
        val userNameOption = parser.accepts("db.username").withRequiredArg().defaultsTo(defaults.getUserName());
        val passwordOption = parser.accepts("db.password").withRequiredArg().defaultsTo(defaults.getPassword());
        val poolNameOption = parser.accepts("db.poolname").withRequiredArg().defaultsTo(defaults.getPoolName());
        val driverClassNameOption = parser.accepts("db.driverclassname").withRequiredArg().defaultsTo(defaults.getDriverClassName());
        val connectionTestQueryOption = parser.accepts("db.connectiontestquery").withRequiredArg().defaultsTo(defaults.getConnectionTestQuery());
        val maxLifetimeOption = parser.accepts("db.maxlifetime").withRequiredArg().ofType(Integer.class).defaultsTo(defaults.getMaxLifetime());
        val idleTimeoutOption = parser.accepts("db.idletimeout").withRequiredArg().ofType(Integer.class).defaultsTo(defaults.getIdleTimeout());
        val dataSourcePropertiesOption = parser.accepts("db.dataSourceProperty").withRequiredArg();
        val options = baseArguments.parse();

        maximumPoolSize = options.valueOf(maximumPoolSizeOption);
        jdbcUrl = options.valueOf(jdbcUrlOption);
        userName = options.valueOf(userNameOption);
        password = options.valueOf(passwordOption);
        poolName = options.valueOf(poolNameOption);
        driverClassName = options.valueOf(driverClassNameOption);
        connectionTestQuery = options.valueOf(connectionTestQueryOption);
        maxLifetime = options.valueOf(maxLifetimeOption);
        idleTimeout = options.valueOf(idleTimeoutOption);

        // put some default properties (
        dataSourceProperties.putAll(defaults.getDataSourceProperties());
        for (val dataSourceProperty : options.valuesOf(dataSourcePropertiesOption))
        {
            val propertyParts = dataSourceProperty.split("=");
            try
            {
                val key = propertyParts[0];
                val value = propertyParts[1];
                if (!Strings.isNullOrEmpty(key) && !Strings.isNullOrEmpty(value))
                {
                    dataSourceProperties.put(key, value);
                    continue;
                }
            }
            catch (ArrayIndexOutOfBoundsException | NumberFormatException e)
            {
                // throw below
            }

            throw new IllegalArgumentException("invalid key=value pair specified for db.dataSourceProperty: " + dataSourceProperty);
        }
    }


    @Getter
    private static class NoDataBaseByDefaultConfig extends DefaultDataBaseConfig
    {
        private final int maximumPoolSize = 0;

        private final String jdbcUrl = null;

        private final String userName = null;

        private final String password = null;

        private final String poolName = null;

        private final String driverClassName = null;

        private final String connectionTestQuery = null;

        private final int maxLifetime = 0;

        private final int idleTimeout = 0;

        private final Map<String, String> dataSourceProperties = null;
    }
}
