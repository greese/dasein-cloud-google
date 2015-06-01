package org.dasein.cloud.ci;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.ReplicapoolSupport;
import org.dasein.cloud.google.network.CIHttpLoadBalancerSupport;

public class GoogleCIServices extends AbstractCIServices<Google> {
    Google google = null;
    public GoogleCIServices(@Nonnull Google google) {
        super(google);
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
        return new CIHttpLoadBalancerSupport(google);
    }

    @Override
    public boolean hasConvergedHttpLoadBalancerSupport() {
        return (getConvergedHttpLoadBalancerSupport() != null);
    }
}
