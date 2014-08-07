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
import org.dasein.cloud.util.NamingConstraints;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class GCEVolumeCapabilities extends AbstractCapabilities<Google> implements VolumeCapabilities {
    public GCEVolumeCapabilities( @Nonnull Google cloud ) {
        super(cloud);
    }

    @Override
    public boolean canAttach( VmState vmState ) throws InternalException, CloudException {
        if( vmState.equals(VmState.RUNNING) ) {
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public boolean canDetach( VmState vmState ) throws InternalException, CloudException {
        if( vmState.equals(VmState.RUNNING) ) {
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public int getMaximumVolumeCount() throws InternalException, CloudException {
        return -2;
    }

    @Override
    public @Nullable Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException {
        return new Storage<Gigabyte>(10000, Storage.GIGABYTE);
    }

    @Override
    public @Nonnull Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException {
        return new Storage<Gigabyte>(1, Storage.GIGABYTE);
    }

    @Override
    public @Nonnull NamingConstraints getVolumeNamingConstraints() throws CloudException, InternalException {
        return null; // TODO: DANGER!
    }

    @Override
    public @Nonnull String getProviderTermForVolume( @Nonnull Locale locale ) {
        return "disk";
    }

    @Override
    public @Nonnull Requirement getVolumeProductRequirement() throws InternalException, CloudException {
        return Requirement.NONE;
    }

    @Override
    public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException {
        return false;
    }

    private static volatile List<String> nonWindowsDevices;

    @Override
    public @Nonnull Iterable<String> listPossibleDeviceIds( @Nonnull Platform platform ) throws InternalException, CloudException {
        if( !platform.isWindows() ) {
            if( nonWindowsDevices == null ) {
                nonWindowsDevices = Collections.unmodifiableList(Arrays.asList("sdf", "sdg", "sdh", "sdi", "sdj", "sdk", "sdl", "sdm", "sdn", "sdo", "sdp", "sdq", "sdr", "sds", "sdt"));

            }
            return nonWindowsDevices;
        }
        else {
            return Collections.emptyList();
        }
    }

    @Override
    public @Nonnull Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException {
        return Collections.singletonList(VolumeFormat.BLOCK);
    }

    @Override
    public @Nonnull Requirement requiresVMOnCreate() throws InternalException, CloudException {
        return Requirement.NONE;
    }

    @Override
    public int getMaximumVolumeProductIOPS() throws InternalException, CloudException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getMaximumVolumeSizeIOPS() throws InternalException, CloudException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getMinimumVolumeProductIOPS() throws InternalException, CloudException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getMinimumVolumeSizeIOPS() throws InternalException, CloudException {
        // TODO Auto-generated method stub
        return 0;
    }
}
