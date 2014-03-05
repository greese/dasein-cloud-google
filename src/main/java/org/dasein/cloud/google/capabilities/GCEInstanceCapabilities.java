package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.util.NamingConstraints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public class GCEInstanceCapabilities extends AbstractCapabilities<Google> implements VirtualMachineCapabilities {
    public GCEInstanceCapabilities(@Nonnull Google cloud){super(cloud);}

    @Override
    public boolean canAlter(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canClone(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canPause(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canReboot(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canResume(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canStart(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canStop(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canSuspend(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canTerminate(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean canUnpause(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int getCostFactor(@Nonnull VmState state) throws CloudException, InternalException {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public String getProviderTermForVirtualMachine(@Nonnull Locale locale) throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nullable
    @Override
    public VMScalingCapabilities getVerticalScalingCapabilities() throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public NamingConstraints getVirtualMachineNamingConstraints() throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Requirement identifyDataCenterLaunchRequirement() throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public Requirement identifyImageRequirement(@Nonnull ImageClass cls) throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public Requirement identifyPasswordRequirement(Platform platform) throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public Requirement identifyShellKeyRequirement(Platform platform) throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Requirement identifySubnetRequirement() throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public Requirement identifyVlanRequirement() throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isAPITerminationPreventable() throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isBasicAnalyticsSupported() throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isExtendedAnalyticsSupported() throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isUserDataSupported() throws CloudException, InternalException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public Iterable<Architecture> listSupportedArchitectures() throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
