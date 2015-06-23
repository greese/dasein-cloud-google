package org.dasein.cloud.google.capabilities;

import java.util.Arrays;
import java.util.Collections;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.VPNCapabilities;
import org.dasein.cloud.network.VPNProtocol;

public class GCEVPNCapabilities extends AbstractCapabilities<Google> implements VPNCapabilities {
    
    public GCEVPNCapabilities(Google provider) {
        super(provider);
    }
    
    @Override
    public Requirement getVPNDataCenterConstraint() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public Iterable<VPNProtocol> listSupportedVPNProtocols() throws CloudException, InternalException {
        return Collections.unmodifiableList(Arrays.asList(VPNProtocol.IKE_V1, VPNProtocol.IKE_V2));
    }

}
