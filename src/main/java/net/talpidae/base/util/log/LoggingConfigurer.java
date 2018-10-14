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

package net.talpidae.base.util.log;

import net.talpidae.base.util.BaseArguments;


public interface LoggingConfigurer
{
    String CONTEXT_INSECT_NAME_KEY = "insectName";

    /**
     * Configure and start the global logger.
     */
    void configure();


    /**
     * Put something in the logging context, which many loggers can use in their log formats.
     */
    void putContext(String key, String value);

    /**
     * Remove an attribute from the logging context.
     */
    void removeContext(String key);

    /**
     * Get the attribute value associated with the specified key.
     */
    String getContext(String key);

    /**
     * Initialize defaults from command line arguments.
     */
    void initializeDefaults(BaseArguments arguments);
}
