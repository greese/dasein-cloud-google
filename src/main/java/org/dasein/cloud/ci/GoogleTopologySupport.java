package org.dasein.cloud.ci;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.google.Google;

public class GoogleTopologySupport extends AbstractTopologySupport<Google> {

    public GoogleTopologySupport(Google provider) {
        super(provider);
        // TODO Auto-generated constructor stub
    }

    @Override
    public String getProviderTermForTopology(Locale locale) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public Iterable<Topology> listTopologies(TopologyFilterOptions options) throws CloudException, InternalException {
        List<Topology> topologies = new ArrayList<Topology>();
        // TODO Auto-generated method stub
        return topologies;
    }

}
