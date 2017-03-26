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

package net.talpidae.base.util;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.Getter;


public class BaseArguments
{
    /**
     * Get and configure the parser instance to your needs, then call parse().
     */
    @Getter
    private final OptionParser optionParser = new OptionParser();

    private final String[] arguments;


    public BaseArguments(String[] args)
    {
        arguments = args;

        // we will call the parser multiple times, so we can't rely on unrecognized options detection
        optionParser.allowsUnrecognizedOptions();
    }


    /**
     * Use the OptionParser to parse the embedded arguments.
     */
    public OptionSet parse()
    {
        return optionParser.parse(arguments);
    }
}
