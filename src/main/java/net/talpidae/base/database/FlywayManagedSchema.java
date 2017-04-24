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

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

import javax.sql.DataSource;


@Slf4j
public class FlywayManagedSchema implements ManagedSchema
{
    private final DataSource dataSource;


    public FlywayManagedSchema(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }


    @Override
    public DataSource migrate()
    {
        log.info("Starting DB migration");

        val flyway = new Flyway();
        flyway.setDataSource(dataSource);

        MigrationInfo current = flyway.info().current();
        if (current == null)
        {
            log.info("No existing schema found");
        }
        else
        {
            log.info("Current schema version is {}", current.getVersion());
        }

        flyway.migrate();

        log.info("Schema migrated to version {}", flyway.info().current().getVersion());

        return dataSource;
    }
}