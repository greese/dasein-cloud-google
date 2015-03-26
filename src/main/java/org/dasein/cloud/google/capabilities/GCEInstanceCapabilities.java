/**
 * Copyright (C) 2012-2015 Dell, Inc
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

import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.util.NamingConstraints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class GCEInstanceCapabilities extends AbstractCapabilities<Google> implements VirtualMachineCapabilities {
    public GCEInstanceCapabilities( @Nonnull Google cloud ) {
        super(cloud);
    }

    @Override
    public boolean canAlter( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canClone( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canPause( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canReboot( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean canResume( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canStart( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean canStop( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean canSuspend( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canTerminate( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean canUnpause( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return false;
    }

    @Override
    public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
        return -2;//GCE has a default limit of 50 but this can be adjusted
    }

    @Override
    public int getCostFactor( @Nonnull VmState state ) throws CloudException, InternalException {
        int costFactor = 0;
        switch( state ) {
            case TERMINATED: {
                costFactor = 0;
                break;
            }
            default: {
                costFactor = 100;
                break;
            }
        }
        return costFactor;
    }

    @Nonnull
    @Override
    public String getProviderTermForVirtualMachine( @Nonnull Locale locale ) throws CloudException, InternalException {
        return "instance";
    }

    @Nullable
    @Override
    public VMScalingCapabilities getVerticalScalingCapabilities() throws CloudException, InternalException {
        return VMScalingCapabilities.getInstance(false, false, Requirement.NONE, Requirement.NONE);
    }

    @Nonnull
    @Override
    public NamingConstraints getVirtualMachineNamingConstraints() throws CloudException, InternalException {
        return NamingConstraints.getAlphaNumeric(1, 63).withRegularExpression("(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)");
    }

    @Nullable
    @Override
    public VisibleScope getVirtualMachineVisibleScope() {
        return VisibleScope.ACCOUNT_DATACENTER;
    }

    @Nullable
    @Override
    public VisibleScope getVirtualMachineProductVisibleScope() {
        return null;
    }

    @Nonnull
    @Override
    public Requirement identifyDataCenterLaunchRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Nonnull
    @Override
    public Requirement identifyImageRequirement( @Nonnull ImageClass cls ) throws CloudException, InternalException {
        return ( cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE );
    }

    @Nonnull
    @Override
    public Requirement identifyPasswordRequirement( Platform platform ) throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyShellKeyRequirement( Platform platform ) throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Nonnull
    @Override
    public Requirement identifySubnetRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyVlanRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public boolean isAPITerminationPreventable() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isBasicAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isExtendedAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isUserDataSupported() throws CloudException, InternalException {
        return false;
    }

    @Nonnull @Override
    public Iterable<Architecture> listSupportedArchitectures() throws InternalException, CloudException {
        //Public images are all 64-bit but there's nothing stopping you from using 32 in a custom image
        return Collections.unmodifiableList(
                Arrays.asList(Architecture.I64, Architecture.I32)
        );
    }

    @Override
    public boolean supportsSpotVirtualMachines() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean supportsAlterVM() {
        return false;
    }

    @Override
    public boolean supportsClone() {
        return true;
    }

    @Override
    public boolean supportsPause() {
        return false;
    }

    @Override
    public boolean supportsReboot() {
        return true;
    }

    @Override
    public boolean supportsResume() {
        return false;
    }

    @Override
    public boolean supportsStart() {
        return false;
    }

    @Override
    public boolean supportsStop() {
        return false;
    }

    @Override
    public boolean supportsSuspend() {
        return false;
    }

    @Override
    public boolean supportsTerminate() {
        return true;
    }

    @Override
    public boolean supportsUnPause() {
        return false;
    }

    @Override
    public boolean isUserDefinedPrivateIPSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsClientRequestToken() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean supportsCloudStoredShellKey() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean isVMProductDCConstrained() throws InternalException, CloudException {
        return false;
    }
}
