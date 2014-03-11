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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class GCEVolumeCapabilities extends AbstractCapabilities<Google> implements VolumeCapabilities{
    public GCEVolumeCapabilities(@Nonnull Google cloud){super(cloud);}

    @Override public boolean canAttach(VmState vmState) throws InternalException, CloudException{
        if(vmState.equals(VmState.RUNNING))return true;
        else return false;
    }

    @Override public boolean canDetach(VmState vmState) throws InternalException, CloudException{
        if(vmState.equals(VmState.RUNNING))return true;
        else return false;
    }

    @Override public int getMaximumVolumeCount() throws InternalException, CloudException{
        return -2;
    }

    @Nullable @Override public Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException{
        return new Storage<Gigabyte>(10000, Storage.GIGABYTE);
    }

    @Nonnull @Override public Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException{
        return new Storage<Gigabyte>(1, Storage.GIGABYTE);
    }

    @Nonnull @Override public String getProviderTermForVolume(@Nonnull Locale locale){
        return "disk";
    }

    @Nonnull @Override public Requirement getVolumeProductRequirement() throws InternalException, CloudException{
        return Requirement.NONE;
    }

    @Override public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException{
        return false;
    }

    @Nonnull @Override public Iterable<String> listPossibleDeviceIds(@Nonnull Platform platform) throws InternalException, CloudException{
        ArrayList<String> list = new ArrayList<String>();

        if( !platform.isWindows()) {
            list.add("/dev/sdf");
            list.add("/dev/sdg");
            list.add("/dev/sdh");
            list.add("/dev/sdi");
            list.add("/dev/sdj");
            list.add("/dev/sdk");
            list.add("/dev/sdl");
            list.add("/dev/sdm");
            list.add("/dev/sdn");
            list.add("/dev/sdo");
            list.add("/dev/sdp");
            list.add("/dev/sdq");
            list.add("/dev/sdr");
            list.add("/dev/sds");
            list.add("/dev/sdt");
        }
        return list;
    }

    @Nonnull @Override public Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException{
        return Collections.singletonList(VolumeFormat.BLOCK);
    }

    @Nonnull @Override public Requirement requiresVMOnCreate() throws InternalException, CloudException{
        return Requirement.NONE;
    }
}
