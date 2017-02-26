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

package net.talpidae.base.resource;


import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.server.ServerConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.WebComponent;

import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


@Slf4j
@ApplicationPath("/*")
public class JerseyApplication extends ResourceConfig
{
    @Inject
    public JerseyApplication(ServerConfig serverConfig)
    {
        val resourceConfig = packages(true, mergePackages(serverConfig));

        if (serverConfig.isLoggingFeatureEnabled())
        {
            resourceConfig.register(LoggingFeature.class);

            Logger.getLogger(LoggingFeature.class.getName()).setLevel(Level.FINEST);
            Logger.getLogger(WebComponent.class.getName()).setLevel(Level.FINEST);
        }

        resourceConfig.property(ServerProperties.RESOURCE_VALIDATION_DISABLE, true);
        resourceConfig.property(ServerProperties.WADL_FEATURE_DISABLE, true);
        resourceConfig.property(ServerProperties.MOXY_JSON_FEATURE_DISABLE, true);
        //.property(ServerProperties.RESOURCE_VALIDATION_DISABLE, true);
    }


    private String[] mergePackages(ServerConfig serverConfig)
    {
        final Set<String> packages = new HashSet<>(Arrays.asList(serverConfig.getJerseyResourcePackages()));

        packages.add(JerseyApplication.class.getPackage().getName());

        return packages.toArray(new String[packages.size()]);
    }
}
