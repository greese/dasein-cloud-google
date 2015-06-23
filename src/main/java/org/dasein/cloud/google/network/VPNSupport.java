package org.dasein.cloud.google.network;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.capabilities.GCEVPNCapabilities;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.VPN;
import org.dasein.cloud.network.VPNCapabilities;
import org.dasein.cloud.network.VPNConnection;
import org.dasein.cloud.network.VPNGateway;
import org.dasein.cloud.network.VPNProtocol;
import org.dasein.cloud.util.APITrace;
public class VPNSupport implements org.dasein.cloud.network.VPNSupport {

    private Google provider;
    private VPNCapabilities capabilities;

    @Override
    public String[] mapServiceAction(ServiceAction action) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void attachToVLAN(String providerVpnId, String providerVlanId) throws CloudException, InternalException {
        APITrace.begin(provider, "attachVPNToVLAN");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void connectToGateway(String providerVpnId, String toGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "connectVPNToGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
    }

    @Override
    public VPN createVPN(String inProviderDataCenterId, String name, String description, VPNProtocol protocol) throws CloudException, InternalException {
        APITrace.begin(provider, "createVPN");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public VPNGateway createVPNGateway(String endpoint, String name, String description, VPNProtocol protocol, String bgpAsn) throws CloudException, InternalException {
        APITrace.begin(provider, "createVPNGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public void deleteVPN(String providerVpnId) throws CloudException, InternalException {
        APITrace.begin(provider, "deleteVPN");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void deleteVPNGateway(String providerVPNGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "deleteVPNGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void detachFromVLAN(String providerVpnId, String providerVlanId) throws CloudException, InternalException {
        APITrace.begin(provider, "detachVPNFromVLAN");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
    }

    @Override
    public void disconnectFromGateway(String providerVpnId, String fromGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "disconnectVPNFromGateway");
        try {
            
        } finally {
            APITrace.end();
        }
        // TODO Auto-generated method stub
        
    }

    @Override
    public VPNCapabilities getCapabilities() throws CloudException, InternalException {
        if (capabilities == null) {
            capabilities = new GCEVPNCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public VPNGateway getGateway(String gatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "getGateway");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public VPN getVPN(String providerVpnId) throws CloudException, InternalException {
        APITrace.begin(provider, "getVPN");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Requirement getVPNDataCenterConstraint() throws CloudException, InternalException {
        return getCapabilities().getVPNDataCenterConstraint();
    }

    @Override
    public Iterable<VPNConnection> listGatewayConnections(String toGatewayId) throws CloudException, InternalException {
        APITrace.begin(provider, "listGatewayConnections");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<ResourceStatus> listGatewayStatus() throws CloudException, InternalException {
        APITrace.begin(provider, "listGatewayStatus");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<VPNGateway> listGateways() throws CloudException, InternalException {
        APITrace.begin(provider, "listGateways");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<VPNGateway> listGatewaysWithBgpAsn(String bgpAsn) throws CloudException, InternalException {
        APITrace.begin(provider, "listGatewaysWithBgpAsn");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<VPNConnection> listVPNConnections(String toVpnId) throws CloudException, InternalException {
        APITrace.begin(provider, "listVPNConnections");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<ResourceStatus> listVPNStatus() throws CloudException, InternalException {
        APITrace.begin(provider, "listVPNStatus");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<VPN> listVPNs() throws CloudException, InternalException {
        APITrace.begin(provider, "listVPNs");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<VPNProtocol> listSupportedVPNProtocols() throws CloudException, InternalException {
        return getCapabilities().listSupportedVPNProtocols();
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        APITrace.begin(provider, "isSubscribed");
        try {
            // TODO Auto-generated method stub
        } finally {
            APITrace.end();
        }
        return false;
    }

}
