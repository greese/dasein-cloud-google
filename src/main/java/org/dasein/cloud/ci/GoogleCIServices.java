package org.dasein.cloud.ci;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.ReplicapoolSupport;

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
        return new GoogleTopologySupport(google);
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
