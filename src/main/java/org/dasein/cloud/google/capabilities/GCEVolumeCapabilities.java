package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VolumeCapabilities;
import org.dasein.cloud.compute.VolumeFormat;
import org.dasein.cloud.google.Google;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public class GCEVolumeCapabilities extends AbstractCapabilities<Google> implements VolumeCapabilities{
    public GCEVolumeCapabilities(@Nonnull Google cloud){super(cloud);}

    @Override public boolean canAttach(VmState vmState) throws InternalException, CloudException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean canDetach(VmState vmState) throws InternalException, CloudException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public int getMaximumVolumeCount() throws InternalException, CloudException{
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nullable @Override public Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public String getProviderTermForVolume(@Nonnull Locale locale){
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Requirement getVolumeProductRequirement() throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Iterable<String> listPossibleDeviceIds(@Nonnull Platform platform) throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Requirement requiresVMOnCreate() throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
