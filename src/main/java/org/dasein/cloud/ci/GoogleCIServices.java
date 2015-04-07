package org.dasein.cloud.ci;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.ReplicapoolSupport;
import org.dasein.cloud.google.network.HttpLoadBalancer;

public class GoogleCIServices extends AbstractCIServices {
    Google google = null;
    public GoogleCIServices(@Nonnull Google google) {
        // TODO Auto-generated constructor stub
        this.google = google;
    }

    @Override
    public @Nullable ConvergedInfrastructureSupport getConvergedInfrastructureSupport() {
        return new ReplicapoolSupport(google);
    }

    @Override
    public @Nullable TopologySupport getTopologySupport() {
        return (TopologySupport)new GoogleTopologySupport(google);
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
        return new HttpLoadBalancer(google);
    }

    @Override
    public boolean hasConvergedHttpLoadBalancerSupport() {
        return true;
    }
}
