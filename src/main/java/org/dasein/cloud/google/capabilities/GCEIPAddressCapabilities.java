package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.IPAddressCapabilities;
import org.dasein.cloud.network.IPVersion;

import javax.annotation.Nonnull;
import java.util.Locale;

public class GCEIPAddressCapabilities extends AbstractCapabilities<Google> implements IPAddressCapabilities{
    public GCEIPAddressCapabilities(@Nonnull Google cloud){super(cloud);}

    @Nonnull @Override public String getProviderTermForIpAddress(@Nonnull Locale locale){
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Requirement identifyVlanForVlanIPRequirement() throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean isAssigned(@Nonnull IPVersion version) throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean canBeAssigned(@Nonnull VmState vmState) throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean isAssignablePostLaunch(@Nonnull IPVersion version) throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean isForwarding(IPVersion version) throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean isRequestable(@Nonnull IPVersion version) throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean supportsVLANAddresses(@Nonnull IPVersion ofVersion) throws InternalException, CloudException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
