package org.dasein.cloud.ci;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.util.NamingConstraints;

public class GCETopologyCapabilities implements TopologyCapabilities {

    public GCETopologyCapabilities(Google provider) {
        // TODO Auto-generated constructor stub
    }

    @Override
    public NamingConstraints getTopologyNamingConstraints() throws CloudException, InternalException {
        return NamingConstraints.getAlphaNumeric(1, 63)
                .withRegularExpression("^[a-z][-a-z0-9]{0,61}[a-z0-9]$")
                .lowerCaseOnly()
                .withNoSpaces()
                .withLastCharacterSymbolAllowed(false)
                .constrainedBy('-');
    }

    @Override
    public TopologyCapabilities getCapabilities() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return null;
    }

}
