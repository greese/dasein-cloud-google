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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEInstanceCapabilities;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.util.APITrace;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class ServerSupport extends AbstractVMSupport {

	private Google provider;
	static private final Logger logger = Google.getLogger(ServerSupport.class);

	public ServerSupport(Google provider){
        super(provider);
        this.provider = provider;
    }

	@Override
	public VirtualMachine alterVirtualMachine(@Nonnull String vmId, @Nonnull VMScalingOptions options) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support altering of existing instances.");
	}

    @Override
    public VirtualMachine modifyInstance(@Nonnull String vmId, @Nonnull String[] firewalls) throws InternalException, CloudException{
        throw new OperationNotSupportedException("GCE does not support altering of existing instances.");
    }

	@Override
	public @Nonnull VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name, @Nonnull String description, boolean powerOn, String... firewallIds) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support cloning of instances via the API.");
	}

	@Override
	public void disableAnalytics(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not currently support analytics.");
	}

	@Override
	public void enableAnalytics(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not currently support analytics.");
	}

    private transient volatile GCEInstanceCapabilities capabilities;
    @Override
    public @Nonnull GCEInstanceCapabilities getCapabilities(){
        if( capabilities == null ) {
            capabilities = new GCEInstanceCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public String getPassword(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE instances do not have passwords");
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
	public VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
        try{
            Compute gce = provider.getGoogleCompute();
            String[] parts = productId.split("\\+");
            MachineTypeList types = gce.machineTypes().list(provider.getContext().getAccountNumber(), parts[1]).setFilter("id eq " + parts[0]).execute();
            for(MachineType type : types.getItems()){
                if(parts[0].equals(type.getId() + ""))return toProduct(type);
            }
            throw new CloudException("The product: " + productId + " could not be found.");
        }
        catch(IOException ex){
            logger.error(ex.getMessage());
            throw new CloudException("An error occurred retrieving the product: " + productId + ": " + ex.getMessage());
        }
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
                    if(instances.getItems() != null && instances.getItems().get(zone) != null && instances.getItems().get(zone).getInstances() != null){
                        for(Instance instance : instances.getItems().get(zone).getInstances()){
                            if(instance.getName().equals(vmId))return toVirtualMachine(instance);
                        }
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
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public @Nonnull VirtualMachine launch(@Nonnull VMLaunchOptions withLaunchOptions)throws CloudException, InternalException {
        APITrace.begin(getProvider(), "launchVM");
        try{
            Compute gce = provider.getGoogleCompute();
            GoogleMethod method = new GoogleMethod(provider);

            if(withLaunchOptions.getDataCenterId() == null || withLaunchOptions.getDataCenterId().equals("")){
                throw new InternalException("A datacenter must be specified when launching an instance");
            }
            if(withLaunchOptions.getVlanId() == null || withLaunchOptions.getVlanId().equals("")){
                throw new InternalException("A VLAN must be specified withn launching an instance");
            }

            //Need to create a Disk with the sourceImage set first
            String diskURL = "";
            Disk disk = new Disk();
            MachineImage image = provider.getComputeServices().getImageSupport().getImage(withLaunchOptions.getMachineImageId());
            disk.setSourceImage((String)image.getTag("contentLink"));
            disk.setName(withLaunchOptions.getFriendlyName());
            disk.setSizeGb(10L);

            Operation job = null;
            try{
                job = gce.disks().insert(provider.getContext().getAccountNumber(), withLaunchOptions.getDataCenterId(), disk).execute();
                diskURL = method.getOperationTarget(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", withLaunchOptions.getDataCenterId(), true);
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred creating the root volume for the instance: " + ex.getMessage());
            }

            Instance instance = new Instance();
            instance.setName(withLaunchOptions.getFriendlyName());
            instance.setDescription(withLaunchOptions.getDescription());
            instance.setMachineType(getProduct(withLaunchOptions.getStandardProductId()).getDescription());

            AttachedDisk rootVolume = new AttachedDisk();
            rootVolume.setBoot(Boolean.TRUE);
            rootVolume.setType("PERSISTENT");
            rootVolume.setSource(diskURL);

            List<AttachedDisk> attachedDisks = new ArrayList<AttachedDisk>();
            attachedDisks.add(rootVolume);
            instance.setDisks(attachedDisks);

            AccessConfig nicConfig = new AccessConfig();
            nicConfig.setName(withLaunchOptions.getFriendlyName() + "NicConfig");
            nicConfig.setType("ONE_TO_ONE_NAT");//Currently the only type supported
            if(withLaunchOptions.getStaticIpIds().length > 0)nicConfig.setNatIP(withLaunchOptions.getStaticIpIds()[0]);
            List<AccessConfig> accessConfigs = new ArrayList<AccessConfig>();
            accessConfigs.add(nicConfig);

            NetworkInterface nic = new NetworkInterface();
            nic.setName(withLaunchOptions.getFriendlyName() + "Nic");
            nic.setNetwork(provider.getNetworkServices().getVlanSupport().getVlan(withLaunchOptions.getVlanId()).getTag("contentLink"));
            nic.setAccessConfigs(accessConfigs);
            List<NetworkInterface> nics = new ArrayList<NetworkInterface>();
            nics.add(nic);
            instance.setNetworkInterfaces(nics);
            instance.setCanIpForward(Boolean.FALSE);

            Scheduling scheduling = new Scheduling();
            scheduling.setAutomaticRestart(Boolean.TRUE);
            scheduling.setOnHostMaintenance("TERMINATE");
            instance.setScheduling(scheduling);

            Map<String,String> keyValues = new HashMap<String, String>();
            if(withLaunchOptions.getBootstrapUser() != null && withLaunchOptions.getBootstrapKey() != null && !withLaunchOptions.getBootstrapUser().equals("") && !withLaunchOptions.getBootstrapKey().equals("")){
                keyValues.put("sshKeys", withLaunchOptions.getBootstrapUser() + ":" + withLaunchOptions.getBootstrapKey());
            }
            if(!withLaunchOptions.getMetaData().isEmpty()) {
                for( Map.Entry<String,Object> entry : withLaunchOptions.getMetaData().entrySet() ) {
                    keyValues.put(entry.getKey(), (String)entry.getValue());
                }
            }
            if (!keyValues.isEmpty()) {
                Metadata metadata = new Metadata();
                ArrayList<Metadata.Items> items = new ArrayList<Metadata.Items>();

                for (Map.Entry<String, String> entry : keyValues.entrySet()) {
                    Metadata.Items item = new Metadata.Items();
                    item.set("key", entry.getKey());
                    item.set("value", entry.getValue());
                    items.add(item);
                }
                metadata.setItems(items);
                instance.setMetadata(metadata);
            }

            Tags tags = new Tags();
            ArrayList<String> tagItems = new ArrayList<String>();
            tagItems.add(withLaunchOptions.getFriendlyName());
            tags.setItems(tagItems);
            instance.setTags(tags);

            String vmId = "";
            try{
                job = gce.instances().insert(provider.getContext().getAccountNumber(), withLaunchOptions.getDataCenterId(), instance).execute();
                vmId = method.getOperationTarget(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", withLaunchOptions.getDataCenterId(), false);
            }
            catch(IOException ex){
                ex.printStackTrace();
                try{
                    gce.disks().delete(provider.getContext().getAccountNumber(), withLaunchOptions.getDataCenterId(), diskURL.substring(diskURL.lastIndexOf("/") + 1)).execute();
                }
                catch(IOException ex1){}
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred launching the instance: " + ex.getMessage());
            }
            if(!vmId.equals("")){
                return getVirtualMachine(vmId);
            }
            else throw new CloudException("Could not find the instance: " + withLaunchOptions.getFriendlyName() + " after launch.");
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public @Nonnull Iterable<String> listFirewalls(@Nonnull String vmId) throws InternalException, CloudException {
        ArrayList<String> firewalls = new ArrayList<String>();
        for(org.dasein.cloud.network.Firewall firewall : provider.getNetworkServices().getFirewallSupport().list()){
            for(String key : firewall.getTags().keySet()){
                if(firewall.getTags().get(key).equals(vmId))firewalls.add(firewall.getName());
            }
        }
        return firewalls;
    }

	@Override
	public @Nonnull Iterable<VirtualMachineProduct> listProducts(@Nonnull Architecture architecture) throws InternalException, CloudException {
        ArrayList<VirtualMachineProduct> products = new ArrayList<VirtualMachineProduct>();
        try{
            Compute gce = provider.getGoogleCompute();
            MachineTypeAggregatedList machineTypes = gce.machineTypes().aggregatedList(provider.getContext().getAccountNumber()).execute();
            Iterator it = machineTypes.getItems().keySet().iterator();
            while(it.hasNext()){
                for(MachineType type : machineTypes.getItems().get(it.next()).getMachineTypes()){
                    //TODO: Filter out deprecated states somehow
                    if (provider.getContext().getRegionId().equals(provider.getDataCenterServices().getDataCenter(type.getZone()).getRegionId())) {
                        VirtualMachineProduct product = toProduct(type);
                        products.add(product);
                    }
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
	public @Nonnull Iterable<VirtualMachine> listVirtualMachines(VMFilterOptions options)throws InternalException, CloudException {
        APITrace.begin(getProvider(), "listVirtualMachines");
        try{
            try{
                ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
                Compute gce = provider.getGoogleCompute();
                InstanceAggregatedList instances = gce.instances().aggregatedList(provider.getContext().getAccountNumber()).execute();
                Iterator<String> it = instances.getItems().keySet().iterator();
                while(it.hasNext()){
                    String zone = it.next();
                    if(getContext().getRegionId().equals(provider.getDataCenterServices().getRegionFromZone(zone))){
                        if(instances.getItems() != null && instances.getItems().get(zone) != null && instances.getItems().get(zone).getInstances() != null){
                            for(Instance instance : instances.getItems().get(zone).getInstances()){
                                VirtualMachine vm = toVirtualMachine(instance);
                                if(options == null || options.matches(vm))vms.add(vm);
                            }
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
		throw new OperationNotSupportedException("GCE does not support suspend/resume of instances.");
	}

    @Override
    public void suspend(@Nonnull String vmId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("GCE does not support suspend/resume of instances.");
    }

	@Override
	public void start(@Nonnull String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support stop/start of instances.");
	}

	@Override
	public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
		throw new OperationNotSupportedException("GCE does not support stop/start of instances.");
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
                Compute gce = provider.getGoogleCompute();
                for(VirtualMachine vm : listVirtualMachines()){
                    if(vm.getProviderVirtualMachineId().equalsIgnoreCase(vmId)){
                        zone = vm.getProviderDataCenterId();
                        job = gce.instances().delete(provider.getContext().getAccountNumber(), zone, vmId).execute();
                        break;
                    }
                }
                if(job != null){
                    GoogleMethod method = new GoogleMethod(provider);
                    if(method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, null, zone)){
                        gce.disks().delete(provider.getContext().getAccountNumber(), zone, vmId).execute();
                    }
                    else{
                        throw new CloudException("An error occurred while terminating the VM. Note: The root disk might also still exist");
                    }
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

    private VirtualMachine toVirtualMachine(Instance instance) throws InternalException, CloudException{
        VirtualMachine vm = new VirtualMachine();
        vm.setProviderVirtualMachineId(instance.getName());
        vm.setName(instance.getName());
        if(instance.getDescription() != null)vm.setDescription(instance.getDescription());
        else vm.setDescription(instance.getName());
        vm.setProviderOwnerId(provider.getContext().getAccountNumber());

        VmState vmState = null;
        if(instance.getStatus().equalsIgnoreCase("provisioning") || instance.getStatus().equalsIgnoreCase("staging"))vmState = VmState.PENDING;
        else if(instance.getStatus().equalsIgnoreCase("stopping"))vmState = VmState.STOPPING;
        else if(instance.getStatus().equalsIgnoreCase("stopped"))vmState = VmState.STOPPED;
        else if(instance.getStatus().equalsIgnoreCase("terminated"))vmState = VmState.TERMINATED;
        else vmState = VmState.RUNNING;
        vm.setCurrentState(vmState);
        try{
            vm.setProviderRegionId(provider.getDataCenterServices().getRegionFromZone(instance.getZone().substring(instance.getZone().lastIndexOf("/") + 1)));
        }
        catch(Exception ex){
            logger.error("An error occurred getting the region for the instance");
            return null;
        }
        String zone = instance.getZone();
        zone = zone.substring(zone.lastIndexOf("/") + 1);
        vm.setProviderDataCenterId(zone);

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse(instance.getCreationTimestamp(), fmt);
        vm.setCreationTimestamp(dt.toDate().getTime());

        for(AttachedDisk disk : instance.getDisks()){
            if(disk.getBoot()){
                String diskName = disk.getSource().substring(disk.getSource().lastIndexOf("/") + 1);
                Compute gce = provider.getGoogleCompute();
                try{
                    Disk sourceDisk = gce.disks().get(provider.getContext().getAccountNumber(), zone, diskName).execute();
                    if (sourceDisk != null && sourceDisk.getSourceImage() != null) {
                        String project = "";
                        Pattern p = Pattern.compile("/projects/(.*?)/");
                        Matcher m = p.matcher(sourceDisk.getSourceImage());
                        while(m.find()){
                            project = m.group(1);
                            break;
                        }
                        vm.setProviderMachineImageId(project + "_" + sourceDisk.getSourceImage().substring(sourceDisk.getSourceImage().lastIndexOf("/") + 1));
                    }
                }
                catch(IOException ex){
                    logger.error(ex.getMessage());
                    throw new InternalException("An error occurred getting the source image of the VM");
                }
            }
        }
        vm.setProductId(instance.getMachineType() + "+" + zone);

        ArrayList<RawAddress> publicAddresses = new ArrayList<RawAddress>();
        ArrayList<RawAddress> privateAddresses = new ArrayList<RawAddress>();
        Boolean firstPass = Boolean.TRUE;
        for(NetworkInterface nic : instance.getNetworkInterfaces()){
            if (firstPass) {
                vm.setProviderVlanId(nic.getNetwork().substring(nic.getNetwork().lastIndexOf("/") + 1));
                firstPass = Boolean.FALSE;
            }
            if (nic.getNetworkIP() != null) {
                privateAddresses.add(new RawAddress(nic.getNetworkIP()));
            }
            for (AccessConfig accessConfig : nic.getAccessConfigs()) {
                if (accessConfig.getNatIP() != null) {
                    publicAddresses.add(new RawAddress(accessConfig.getNatIP()));
                }
            }
        }
        vm.setPublicAddresses(publicAddresses.toArray(new RawAddress[publicAddresses.size()]));
        vm.setPrivateAddresses(privateAddresses.toArray(new RawAddress[privateAddresses.size()]));

        vm.setRebootable(true);
        vm.setPersistent(true);
        vm.setIpForwardingAllowed(true);
        vm.setImagable(false);
        vm.setClonable(false);

        vm.setTag("contentLink", instance.getSelfLink());

        return vm;
    }

    private VirtualMachineProduct toProduct(MachineType machineType){
        VirtualMachineProduct product = new VirtualMachineProduct();
        product.setProviderProductId(machineType.getId() + "+" + machineType.getZone());
        product.setName(machineType.getName());
        product.setDescription(machineType.getSelfLink());//Used to address but can't be used in a filter hence keeping IDs
        product.setCpuCount(machineType.getGuestCpus());
        product.setRamSize(new Storage<Megabyte>(machineType.getMemoryMb(), Storage.MEGABYTE));
        product.setRootVolumeSize(new Storage<Gigabyte>(machineType.getImageSpaceGb(), Storage.GIGABYTE));
        product.setVisibleScope(VisibleScope.ACCOUNT_DATACENTER);
        return product;
    }
}
