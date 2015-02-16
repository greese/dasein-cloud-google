package org.dasein.cloud.ci;

import javax.annotation.Nullable;

import org.dasein.cloud.google.compute.server.ReplicapoolSupport;

public class GoogleCIServices extends AbstractCIServices {
    @Override
    public @Nullable ConvergedInfrastructureSupport getConvergedInfrastructureSupport() {
        return new ReplicapoolSupport(null);
    }

    @Override
    public @Nullable TopologySupport getTopologySupport() {
        return new GoogleTopologySupport(null);
    }

    @Override
    public boolean hasConvergedInfrastructureSupport() {
        return (getConvergedInfrastructureSupport() == null);
    }

    @Override
    public boolean hasTopologySupport() {
        return (getTopologySupport() == null);
    }
}
