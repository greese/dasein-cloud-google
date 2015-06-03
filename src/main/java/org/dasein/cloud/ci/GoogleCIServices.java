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
