package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.compute.SnapshotCapabilities;
import org.dasein.cloud.google.Google;

import javax.annotation.Nonnull;
import java.util.Locale;

public class GCESnapshotCapabilities extends AbstractCapabilities<Google> implements SnapshotCapabilities{
    public GCESnapshotCapabilities(@Nonnull Google cloud){super(cloud);}

    @Nonnull @Override public String getProviderTermForSnapshot(@Nonnull Locale locale){
        return "snapshot";
    }

    @Nonnull @Override public Requirement identifyAttachmentRequirement() throws InternalException, CloudException{
        return Requirement.OPTIONAL;
    }

    @Override public boolean supportsSnapshotCopying() throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean supportsSnapshotCreation() throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean supportsSnapshotSharing() throws InternalException, CloudException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean supportsSnapshotSharingWithPublic() throws InternalException, CloudException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
