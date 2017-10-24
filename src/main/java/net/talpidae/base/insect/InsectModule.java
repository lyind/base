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

package net.talpidae.base.insect;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

import net.talpidae.base.insect.config.DefaultQueenSettings;
import net.talpidae.base.insect.config.DefaultSlaveSettings;
import net.talpidae.base.insect.config.QueenSettings;
import net.talpidae.base.insect.config.SlaveSettings;
import net.talpidae.base.insect.metrics.MetricsSink;
import net.talpidae.base.insect.metrics.QueuedMetricsSink;


public class InsectModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        OptionalBinder.newOptionalBinder(binder(), QueenSettings.class).setDefault().to(DefaultQueenSettings.class);
        OptionalBinder.newOptionalBinder(binder(), Queen.class).setDefault().to(AsyncQueen.class);

        OptionalBinder.newOptionalBinder(binder(), SlaveSettings.class).setDefault().to(DefaultSlaveSettings.class);
        OptionalBinder.newOptionalBinder(binder(), Slave.class).setDefault().to(AsyncSlave.class);

        OptionalBinder.newOptionalBinder(binder(), MetricsSink.class);
    }
}