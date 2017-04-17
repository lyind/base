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

package net.talpidae.base.client;

import com.google.inject.Inject;
import com.google.inject.Provider;


public class GenericLoadBalancingProxyWebTargetProvider<T> implements Provider<T>
{
    private final LoadBalancingProxyWebTarget<T> webTarget;

    @Inject
    public GenericLoadBalancingProxyWebTargetProvider(LoadBalancingProxyWebTarget<T> webTarget)
    {
        this.webTarget = webTarget;
    }

    @Override
    public T get()
    {
        return webTarget.getProxyWebTarget();
    }
}