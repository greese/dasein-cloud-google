/**
 * Copyright (C) 2012-2015 Dell, Inc
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.ci;

import javax.annotation.Nullable;

import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.ReplicapoolSupport;
import org.dasein.cloud.google.network.CIHttpLoadBalancerSupport;

public class GoogleCIServices extends AbstractCIServices<Google> {

    public GoogleCIServices(Google provider) {
        super(provider);
    }

    @Override
    public @Nullable ConvergedInfrastructureSupport getConvergedInfrastructureSupport() {
        return new ReplicapoolSupport(getProvider());
    }

    @Override
    public @Nullable TopologySupport getTopologySupport() {
        return new GoogleTopologySupport(getProvider());
    }

    @Override
    public boolean hasConvergedInfrastructureSupport() {
        return (getConvergedInfrastructureSupport() == null);
    }

    @Override
    public boolean hasTopologySupport() {
        return (getTopologySupport() == null);
    }

    @Override
    public ConvergedHttpLoadBalancerSupport getConvergedHttpLoadBalancerSupport() {
        return new CIHttpLoadBalancerSupport(getProvider());
    }

    @Override
    public boolean hasConvergedHttpLoadBalancerSupport() {
        return (getConvergedHttpLoadBalancerSupport() != null);
    }
}
