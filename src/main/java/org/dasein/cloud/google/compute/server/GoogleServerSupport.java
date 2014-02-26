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

package org.dasein.cloud.google.compute.server;

import java.io.IOException;
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.util.APITrace;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Implements the compute services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleServerSupport extends AbstractVMSupport {

	private Google provider;
	static private final Logger logger = Google.getLogger(GoogleServerSupport.class);

	public GoogleServerSupport(Google provider){
        super(provider);
        this.provider = provider;
    }

	@Override
	public VirtualMachine alterVirtualMachine(@Nonnull String vmId, @Nonnull VMScalingOptions options) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support altering of existing instances.");
	}

	@Override
	public @Nonnull VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name, @Nonnull String description, boolean powerOn, String... firewallIds) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support cloning of instances via the API.");
	}

	@Override
	public VMScalingCapabilities describeVerticalScalingCapabilities() throws CloudException, InternalException {
		return VMScalingCapabilities.getInstance(false, false, Requirement.NONE, Requirement.NONE);
	}

	@Override
	public void disableAnalytics(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not currently support analytics.");
	}

	@Override
	public void enableAnalytics(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not currently support analytics.");
	}

    public @Nonnull Iterable<VmState> getTerminateVMStates(@Nullable VirtualMachine vm) {
        return new ArrayList<VmState>(Arrays.asList(VmState.values()));
    }

    @Override
    public String getPassword(@Nonnull String vmId) throws InternalException, CloudException {
        return "";
        //TODO
    }

	@Override
	public @Nonnull String getConsoleOutput(@Nonnull String vmId) throws InternalException, CloudException {
		try{
            for(VirtualMachine vm : listVirtualMachines()){
                if(vm.getProviderVirtualMachineId().equalsIgnoreCase(vmId)){
                    Compute gce = provider.getGoogleCompute();
                    SerialPortOutput output = gce.instances().getSerialPortOutput(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), vmId).execute();
                    return output.getContents();
                }
            }
        }
        catch(IOException ex){
            logger.error(ex.getMessage());
            throw new CloudException("An error occurred when getting console output for VM: " + vmId + ": " + ex.getMessage());
        }
        throw new InternalException("The Virtual Machine: " + vmId + " could not be found.");
	}

	@Override
	public int getCostFactor(@Nonnull VmState state) throws InternalException, CloudException {
        int costFactor = 0;
        switch(state){
            case TERMINATED:{
                costFactor = 0;
                break;
            }
            default:{
                costFactor = 100;
                break;
            }
        }
        return costFactor;
	}

	@Override
	public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
        //GCE limits by CPU count in a region (currently 24). As there's no way to establish the number of VMs this will need to remain unknown
		return -2;
	}

	@Override
	public VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
        try{
            Compute gce = provider.getGoogleCompute();
            MachineTypeAggregatedList types = gce.machineTypes().aggregatedList(provider.getContext().getAccountNumber()).setFilter("name eq " + productId).execute();
            Iterator<String> zones = types.getItems().keySet().iterator();
            while(zones.hasNext()){
                String zone = zones.next();
                for(MachineType type : types.getItems().get(zone).getMachineTypes()){
                    if(type.getName().equals(productId))return toProduct(type);
                }
            }
            throw new CloudException("The product: " + productId + " could not be found.");
        }
        catch(IOException ex){
            logger.error(ex.getMessage());
            throw new CloudException("An error occurred retrieving the product: " + productId + ": " + ex.getMessage());
        }
	}

	@Override
	public @Nonnull String getProviderTermForServer(@Nonnull Locale locale) {
		return "instance";
	}

	@Override
	public VirtualMachine getVirtualMachine(@Nonnull String vmId)throws InternalException, CloudException {
        APITrace.begin(getProvider(), "getVirtualMachine");
        try{
            try{
                Compute gce = provider.getGoogleCompute();
                InstanceAggregatedList instances = gce.instances().aggregatedList(provider.getContext().getAccountNumber()).setFilter("name eq " + vmId).execute();
                Iterator<String> it = instances.getItems().keySet().iterator();
                while(it.hasNext()){
                    String zone = it.next();
                    for(Instance instance : instances.getItems().get(zone).getInstances()){
                        if(instance.getName().equals(vmId))return toVirtualMachine(instance);
                    }
                }
                throw new CloudException("The Virtual Machine: " + vmId + " could not be found.");
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred retrieving VM: " + vmId + ": " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public @Nonnull Requirement identifyImageRequirement(ImageClass cls) throws CloudException, InternalException {
		return (cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE);
	}

	@Override
	public @Nonnull Requirement identifyPasswordRequirement(Platform platform)throws CloudException, InternalException {
        return Requirement.NONE;
	}

	@Override
	public @Nonnull Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public @Nonnull Requirement identifyShellKeyRequirement(Platform platform) throws CloudException, InternalException {
        return Requirement.NONE;
	}

	@Override
	public @Nonnull Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public @Nonnull Requirement identifyVlanRequirement() throws CloudException, InternalException {
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
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isUserDataSupported() throws CloudException, InternalException {
		//TODO: Check user data capabilities
		return false;
	}

	@Override
	public @Nonnull VirtualMachine launch(@Nonnull VMLaunchOptions withLaunchOptions)throws CloudException, InternalException {
        APITrace.begin(getProvider(), "launchVM");
        try{
            Compute gce = provider.getGoogleCompute();
            GoogleMethod method = new GoogleMethod(provider);

            //Need to create a Disk with the sourceImage set first
            String diskURL = "";
            Disk disk = new Disk();
            disk.setSourceImage(withLaunchOptions.getMachineImageId());
            disk.setName(withLaunchOptions.getFriendlyName());
            disk.setSizeGb(10L);

            try{
                Operation job = gce.disks().insert(provider.getContext().getAccountNumber(), withLaunchOptions.getDataCenterId(), disk).execute();
                diskURL = method.getOperationTarget(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", withLaunchOptions.getDataCenterId(), true);
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred creating the root volume for the instance: " + ex.getMessage());
            }

            Instance instance = new Instance();
            instance.setMachineType(withLaunchOptions.getStandardProductId());

            AttachedDisk rootVolume = new AttachedDisk();
            rootVolume.setBoot(Boolean.TRUE);
            rootVolume.setType("PERSISTENT");
            rootVolume.setSource(diskURL);
            List<AttachedDisk> attachedDisks = new ArrayList<AttachedDisk>();
            attachedDisks.add(rootVolume);
            instance.setDisks(attachedDisks);


        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public @Nonnull Iterable<String> listFirewalls(@Nonnull String vmId) throws InternalException, CloudException {
        //TODO: Implement me
        return null;
	}

	@Override
	public @Nonnull Iterable<VirtualMachineProduct> listProducts(@Nonnull Architecture architecture) throws InternalException, CloudException {
        ArrayList<VirtualMachineProduct> products = new ArrayList<VirtualMachineProduct>();
        try{
            Compute gce = provider.getGoogleCompute();
            MachineTypeAggregatedList machineTypes = gce.machineTypes().aggregatedList(provider.getContext().getAccountNumber()).execute();
            Iterator it = machineTypes.getItems().keySet().iterator();
            ArrayList<String> addedProducts = new ArrayList<String>();
            while(it.hasNext()){
                for(MachineType type : machineTypes.getItems().get(it.next()).getMachineTypes()){
                    //TODO: Filter out deprecated states somehow
                    VirtualMachineProduct product = toProduct(type);
                    if(!addedProducts.contains(product.getProviderProductId()))products.add(product);
                    addedProducts.add(product.getProviderProductId());
                }
            }
            return products;
        }
        catch(IOException ex){
            logger.error(ex.getMessage());
            throw new CloudException("An error occurred listing VM products.");
        }
	}

	@Override
	public Iterable<Architecture> listSupportedArchitectures()throws InternalException, CloudException {
        //Public images are all 64-bit but there's nothing stopping you from using 32 in a custom image
        ArrayList<Architecture> list = new ArrayList<Architecture>();
        list.add(Architecture.I64);
        list.add(Architecture.I32);
        return list;
	}

	@Override
	public @Nonnull Iterable<VirtualMachine> listVirtualMachines(VMFilterOptions options)throws InternalException, CloudException {
        APITrace.begin(getProvider(), "listVirtualMachines");
        try{
            try{
                ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
                Compute gce = provider.getGoogleCompute();
                //TODO: Set filter(s)
                InstanceAggregatedList instances = gce.instances().aggregatedList(provider.getContext().getAccountNumber()).execute();
                Iterator<String> it = instances.getItems().keySet().iterator();
                while(it.hasNext()){
                    String zone = it.next();
                    if(instances.getItems() != null && instances.getItems().get(zone)!= null && instances.getItems().get(zone).getInstances() != null){
                        for(Instance instance : instances.getItems().get(zone).getInstances()){
                            vms.add(toVirtualMachine(instance));
                        }
                    }
                }
                return vms;
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred while listing Virtual Machines.");
            }
        }
        finally{
            APITrace.end();
        }
	}

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines()throws InternalException, CloudException {
        VMFilterOptions options = VMFilterOptions.getInstance();
        return listVirtualMachines(options);
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
        ArrayList<ResourceStatus> vmStatuses = new ArrayList<ResourceStatus>();
        for(VirtualMachine vm : listVirtualMachines()){
            ResourceStatus status = new ResourceStatus(vm.getProviderVirtualMachineId(), vm.getCurrentState());
            vmStatuses.add(status);
        }
        return vmStatuses;
    }

	@Override
	public void pause(@Nonnull String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support pausing vms.");
	}

	@Override
	public void reboot(@Nonnull String vmId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "rebootVM");
        try{
            try{
                Operation job = null;
                String zone = null;
                for(VirtualMachine vm : listVirtualMachines()){
                    if(vm.getProviderVirtualMachineId().equalsIgnoreCase(vmId)){
                        zone = vm.getProviderDataCenterId();
                        Compute gce = provider.getGoogleCompute();
                        job = gce.instances().reset(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), vmId).execute();
                        break;
                    }
                }
                if(job != null){
                    GoogleMethod method = new GoogleMethod(provider);
                    method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, null, zone);
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred while rebooting VM: " + vmId + ": " + ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
	}

	@Override
	public void resume(@Nonnull String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("GCE does not support pausing vms.");
	}

	@Override
	public void start(@Nonnull String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support pausing vms.");
	}

	@Override
	public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support pausing vms.");
	}

	@Override
	public boolean supportsPauseUnpause(@Nonnull VirtualMachine vm) {
		return false;
	}

	@Override
	public boolean supportsStartStop(@Nonnull VirtualMachine vm) {
		return false;
	}

	@Override
	public boolean supportsSuspendResume(@Nonnull VirtualMachine vm) {
		return false;
	}

	@Override
	public void suspend(@Nonnull String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("GCE does not support suspending vms.");
	}

	@Override
	public void terminate(@Nonnull String vmId) throws InternalException, CloudException {
        terminate(vmId, null);
	}

    @Override
    public void terminate(@Nonnull String vmId, String reason) throws InternalException, CloudException{
        APITrace.begin(getProvider(), "terminateVM");
        try{
            try{
                Operation job = null;
                String zone = null;
                for(VirtualMachine vm : listVirtualMachines()){
                    if(vm.getProviderVirtualMachineId().equalsIgnoreCase(vmId)){
                        zone = vm.getProviderDataCenterId();
                        Compute gce = provider.getGoogleCompute();
                        job = gce.instances().delete(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), vmId).execute();
                        break;
                    }
                }
                if(job != null){
                    GoogleMethod method = new GoogleMethod(provider);
                    method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, null, zone);
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred while terminating VM: " + vmId + ": " + ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
    }

	@Override
	public void unpause(@Nonnull String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("GCE does not support unpausing vms.");
	}

	@Override
	public void updateTags(String vmId, Tag... tags) throws CloudException, InternalException {
		updateTags(new String[]{vmId}, tags);
	}

	@Override
	public void updateTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
        //TODO: Implement me
	}

	@Override
	public void removeTags(String vmId, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support removing meta data from vms");
	}

	@Override
	public void removeTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support removing meta data from vms");
	}

    private VirtualMachine toVirtualMachine(Instance instance){
        VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(instance.getName());
        vm.setName(instance.getName());
        vm.setDescription(instance.getDescription());
        vm.setProviderOwnerId(provider.getContext().getAccountNumber());

        VmState vmState = null;
        if(instance.getStatus().equalsIgnoreCase("provisioning") || instance.getStatus().equalsIgnoreCase("staging"))vmState = VmState.PENDING;
        else if(instance.getStatus().equalsIgnoreCase("stopping"))vmState = VmState.STOPPING;
        else if(instance.getStatus().equalsIgnoreCase("stopped"))vmState = VmState.STOPPED;
        else if(instance.getStatus().equalsIgnoreCase("terminated"))vmState = VmState.TERMINATED;
        else vmState = VmState.RUNNING;
        vm.setCurrentState(vmState);
        vm.setProviderRegionId(provider.getDataCenterServices().getRegionFromZone(instance.getZone()));
        vm.setProviderDataCenterId(instance.getZone());

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse(instance.getCreationTimestamp(), fmt);
        vm.setCreationTimestamp(dt.toDate().getTime());

        //TODO: Product/Image
        //TODO: Networks
        //TODO: Disks

        vm.setRebootable(true);
        vm.setPersistent(true);
        vm.setIpForwardingAllowed(true);
        vm.setImagable(false);
        vm.setClonable(true);

        return vm;
    }

    private VirtualMachineProduct toProduct(MachineType machineType){
        VirtualMachineProduct product = new VirtualMachineProduct();
        product.setProviderProductId(machineType.getName());
        product.setName(machineType.getName());
        product.setDescription(machineType.getDescription());
        product.setCpuCount(machineType.getGuestCpus());
        product.setRamSize(new Storage<Megabyte>(machineType.getMemoryMb(), Storage.MEGABYTE));
        product.setRootVolumeSize(new Storage<Gigabyte>(machineType.getImageSpaceGb(), Storage.GIGABYTE));
        return product;
    }
}
