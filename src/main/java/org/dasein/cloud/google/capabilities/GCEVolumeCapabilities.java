/**
 * Copyright (C) 2012-2013 Dell, Inc
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

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

    @Override
    public @Nonnull Iterable<String> listPossibleDeviceIds(@Nonnull Platform platform) throws InternalException, CloudException {
        ArrayList<String> list = new ArrayList<String>();

        if( !platform.isWindows()) {
            list.add("sdf");
            list.add("sdg");
            list.add("sdh");
            list.add("sdi");
            list.add("sdj");
            list.add("sdk");
            list.add("sdl");
            list.add("sdm");
            list.add("sdn");
            list.add("sdo");
            list.add("sdp");
            list.add("sdq");
            list.add("sdr");
            list.add("sds");
            list.add("sdt");
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
