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

package org.dasein.cloud.google.compute.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.compute.AbstractVMSupport;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VMFilterOptions;
import org.dasein.cloud.compute.VMLaunchOptions;
import org.dasein.cloud.compute.VMScalingOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VirtualMachineProductFilterOptions;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VolumeAttachment;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.capabilities.GCEInstanceCapabilities;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.cloud.util.NamingConstraints;
import org.dasein.util.Jiterator;
import org.dasein.util.JiteratorPopulator;
import org.dasein.util.PopulatorThread;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.dasein.util.uom.time.Day;
import org.dasein.util.uom.time.TimePeriod;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceAggregatedList;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.MachineTypeAggregatedList;
import com.google.api.services.compute.model.MachineTypeList;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.SerialPortOutput;
import com.google.api.services.compute.model.Tags;

public class ServerSupport extends AbstractVMSupport {

	private Google provider;
	static private final Logger logger = Google.getLogger(ServerSupport.class);
	private Cache<MachineTypeAggregatedList> machineTypesCache;
	public ServerSupport(Google provider){
        super(provider);
        this.provider = provider;
        machineTypesCache = Cache.getInstance(provider, "MachineTypes", MachineTypeAggregatedList.class, CacheLevel.CLOUD, new TimePeriod<Day>(1, TimePeriod.DAY));
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
		} catch (IOException ex) {
			logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException("An error occurred when getting console output for VM: " + vmId + ": " + ex.getMessage());
		}
        throw new InternalException("The Virtual Machine: " + vmId + " could not be found.");
	}

	@Override
	public VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
        try{
            Compute gce = provider.getGoogleCompute();
            String[] parts = productId.split("\\+");
            if ((parts != null) && (parts.length > 1)) {
                MachineTypeList types = gce.machineTypes().list(provider.getContext().getAccountNumber(), parts[1]).setFilter("name eq " + parts[0]).execute();
                for(MachineType type : types.getItems()){
                    if(parts[0].equals(type.getName()))return toProduct(type);
                }
            }
            return null;  // Tests indicate null should come back, rather than exception
            //throw new CloudException("The product: " + productId + " could not be found.");
		} catch (IOException ex) {
			logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
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
                return null; // not found
            } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException("An error occurred retrieving VM: " + vmId + ": " + ex.getMessage());
			}
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        listVirtualMachines();
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

            Instance instance = new Instance();
            instance.setName(withLaunchOptions.getHostName());
            instance.setDescription(withLaunchOptions.getDescription());
            if (withLaunchOptions.getStandardProductId().contains("+")) {
                instance.setMachineType(getProduct(withLaunchOptions.getStandardProductId()).getDescription());
            } else {
                instance.setMachineType(getProduct(withLaunchOptions.getStandardProductId() + "+" + withLaunchOptions.getDataCenterId()).getDescription());
            }

            MachineImage image = provider.getComputeServices().getImageSupport().getImage(withLaunchOptions.getMachineImageId());

            AttachedDisk rootVolume = new AttachedDisk();
            rootVolume.setBoot(Boolean.TRUE);
            rootVolume.setType("PERSISTENT");
            rootVolume.setMode("READ_WRITE");
            AttachedDiskInitializeParams params = new AttachedDiskInitializeParams();
            // do not use withLaunchOptions.getFriendlyName() it is non compliant!!!
            params.setDiskName(withLaunchOptions.getHostName());            
            try {
                String[] parts = withLaunchOptions.getMachineImageId().split("_");
                Image img = gce.images().get(parts[0], parts[1]).execute();
                Long size = img.getDiskSizeGb();
                String diskSizeGb = size.toString();
                if (null == diskSizeGb) {
                    diskSizeGb = img.getUnknownKeys().get("diskSizeGb").toString();
                }
                Long MinimumDiskSizeGb = Long.valueOf(diskSizeGb).longValue();
                params.setDiskSizeGb(MinimumDiskSizeGb); 
            } catch ( Exception e ) {
                params.setDiskSizeGb(10L);
            }
            if ((image != null) && (image.getTag("contentLink") != null))
                params.setSourceImage((String)image.getTag("contentLink"));
            else
                throw new CloudException("Problem getting the contentLink tag value from the image for " + withLaunchOptions.getMachineImageId());
            rootVolume.setInitializeParams(params);

            if(withLaunchOptions.getVolumes().length > 0){
                for(VolumeAttachment volume : withLaunchOptions.getVolumes()){
                    //TODO: Specify new and existing volumes
                }
            }

            List<AttachedDisk> attachedDisks = new ArrayList<AttachedDisk>();
            attachedDisks.add(rootVolume);
            instance.setDisks(attachedDisks);

            AccessConfig nicConfig = new AccessConfig();
            nicConfig.setName("External NAT");
            nicConfig.setType("ONE_TO_ONE_NAT");//Currently the only type supported
            if(withLaunchOptions.getStaticIpIds().length > 0)nicConfig.setNatIP(withLaunchOptions.getStaticIpIds()[0]);
            List<AccessConfig> accessConfigs = new ArrayList<AccessConfig>();
            accessConfigs.add(nicConfig);

            NetworkInterface nic = new NetworkInterface();
            nic.setName("nic0");
            if (null != withLaunchOptions.getVlanId()) {
                nic.setNetwork(provider.getNetworkServices().getVlanSupport().getVlan(withLaunchOptions.getVlanId()).getTag("contentLink"));
            } else {
                nic.setNetwork(provider.getNetworkServices().getVlanSupport().getVlan("default").getTag("contentLink"));
            }
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
                    if ((entry.getValue() == null) || (entry.getValue().isEmpty() == true) || (entry.getValue().equals("")))
                        item.set("value", ""); // GCE HATES nulls...
                    else 
                        item.set("value", entry.getValue());
                    items.add(item);
                }
                metadata.setItems(items);
                instance.setMetadata(metadata);
            }

            Tags tags = new Tags();
            ArrayList<String> tagItems = new ArrayList<String>();
            tagItems.add(withLaunchOptions.getHostName()); // Each tag must be 1-63 characters long, and comply with RFC1035
            tags.setItems(tagItems);
            instance.setTags(tags);

            String vmId = "";
            try{
            	Operation job = gce.instances().insert(provider.getContext().getAccountNumber(), withLaunchOptions.getDataCenterId(), instance).execute();
                vmId = method.getOperationTarget(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", withLaunchOptions.getDataCenterId(), false);
	        } catch (IOException ex) {
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException("An error occurred launching the instance: " + ex.getMessage());
			} catch (Exception e) {
			    if ((e.getMessage().contains("The resource")) && 
                        (e.getMessage().contains("disks")) &&
                        (e.getMessage().contains("already exists"))) {
			        throw new CloudException("A disk named '" + withLaunchOptions.getFriendlyName() + "' already exists.");
			    } else {
			        throw new CloudException(e);
			    }
			}
            if(!vmId.equals("")){
                return getVirtualMachine(vmId);
            } else {
                throw new CloudException("Could not find the instance: " + withLaunchOptions.getFriendlyName() + " after launch.");
            }
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

	public @Nonnull Iterable<VirtualMachineProduct> listProducts(@Nonnull Architecture architecture, String preferredDataCenterId) throws InternalException, CloudException {
        MachineTypeAggregatedList machineTypes = null;

        Compute gce = provider.getGoogleCompute();
        Iterable<MachineTypeAggregatedList> machineTypesCachedList = machineTypesCache.get(provider.getContext());

        if (machineTypesCachedList != null) {
            Iterator<MachineTypeAggregatedList> machineTypesCachedListIterator = machineTypesCachedList.iterator();
            if (machineTypesCachedListIterator.hasNext())
                machineTypes = machineTypesCachedListIterator.next();
        } else {
            try {
                machineTypes = gce.machineTypes().aggregatedList(provider.getContext().getAccountNumber()).execute();
                machineTypesCache.put(provider.getContext(), Arrays.asList(machineTypes));
            } catch (IOException ex) {
                logger.error(ex.getMessage());
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException("An error occurred listing VM products.");
            }
        }

        Collection<VirtualMachineProduct> products = new ArrayList<VirtualMachineProduct>();

            Iterator<String> it = machineTypes.getItems().keySet().iterator();
            while(it.hasNext()){
                Object dataCenterId = it.next();
                if ((preferredDataCenterId == null) || (dataCenterId.toString().endsWith(preferredDataCenterId)))
                    for(MachineType type : machineTypes.getItems().get(dataCenterId).getMachineTypes()){
                       //TODO: Filter out deprecated states somehow
                       if ((preferredDataCenterId == null) || (type.getZone().equals(preferredDataCenterId))) {
                           VirtualMachineProduct product = toProduct(type);
                           products.add(product);
                       }
                   }
            }

        return products;
    }

    @Override
    public Iterable<VirtualMachineProduct> listProducts(VirtualMachineProductFilterOptions options, Architecture architecture) throws InternalException, CloudException{
        if ((architecture == null) || (Architecture.I64 == architecture)) { // GCE only has I64 architecture
            String dataCenterId = null;
            if (options != null)
                dataCenterId = options.getDataCenterId();
            Iterable<VirtualMachineProduct> result = listProducts(Architecture.I64, dataCenterId);
            return result;
        } else
            return new ArrayList<VirtualMachineProduct>(); // empty!
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
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
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
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
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
        Compute gce = provider.getGoogleCompute();
        try {
            VirtualMachine vm = getVirtualMachine(vmId);
            gce.instances().start(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), vmId).execute();
        } catch (IOException ex) {
            logger.error(ex.getMessage());
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred while rebooting VM: " + vmId + ": " + ex.getMessage());
        }
    }

    @Override
    public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
        Compute gce = provider.getGoogleCompute();
        try {
            VirtualMachine vm = getVirtualMachine(vmId);
            gce.instances().stop(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), vmId.toString()).execute();
        } catch (IOException ex) {
            logger.error(ex.getMessage());
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred while rebooting VM: " + vmId + ": " + ex.getMessage());
        }
    }

    @Override
    public void terminate(@Nonnull String vmId) throws InternalException, CloudException {
        VirtualMachine vm = getVirtualMachine(vmId);
        terminateVm(vmId);
        terminateVmDisk(vmId, vm.getProviderDataCenterId());
    }

    @Override
    public void terminate(@Nonnull String vmId, String reason) throws InternalException, CloudException{
        VirtualMachine vm = getVirtualMachine(vmId);
        terminateVm(vmId, null);
        terminateVmDisk(vmId, vm.getProviderDataCenterId());
    }

    public void terminateVm(@Nonnull String vmId) throws InternalException, CloudException {
        terminateVm(vmId, null);
    }

    public void terminateVm(@Nonnull String vmId, String reason) throws InternalException, CloudException {
        try {
            APITrace.begin(getProvider(), "terminateVM");
            Operation job = null;
            GoogleMethod method = null;
            String zone = null;
            Compute gce = provider.getGoogleCompute();
            VirtualMachine vm = getVirtualMachine(vmId);

            if (null == vm) {
                throw new CloudException("Virtual Machine " + vmId + " was not found.");
            }

            try {
                zone = vm.getProviderDataCenterId();
                job = gce.instances().delete(provider.getContext().getAccountNumber(), zone, vmId).execute();
                if(job != null) {
                    method = new GoogleMethod(provider);
                    if (false == method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, null, zone)) {
                        throw new CloudException("An error occurred while terminating the VM. Note: The root disk might also still exist");
                    }
                }
            } catch (IOException ex) {
                logger.error(ex.getMessage());
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException("An error occurred while terminating VM: " + vmId + ": " + ex.getMessage());
            } catch (Exception ex) {
                throw new CloudException(ex); // catch exception from getOperationComplete
            }

        } finally {
            APITrace.end();
        }
    }

    public void terminateVmDisk(@Nonnull String diskName, String zone) throws InternalException, CloudException {
        try {
            APITrace.begin(getProvider(), "terminateVM");
            try {
                Compute gce = provider.getGoogleCompute();
                Operation job = gce.disks().delete(provider.getContext().getAccountNumber(), zone, diskName).execute();
                GoogleMethod method = new GoogleMethod(provider);
                method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, null, zone);
            } catch (IOException ex) {
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    if ((404 == gjre.getStatusCode()) &&
                        (gjre.getStatusMessage().equals("Not Found"))) {
                        // remain silent. this happens when instance is created with delete root volume on terminate is selected.
                        //throw new CloudException("Virtual Machine disk image '" + vmId + "' was not found.");
                    } else {
                        throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                    }
                } else
                    throw new CloudException("An error occurred while deleting VM disk: " + diskName + ": " + ex.getMessage());
            } catch (Exception ex) {
                throw new CloudException(ex); // catch exception from getOperationComplete
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
        if (instance.getDescription() != null) {
            vm.setDescription(instance.getDescription());
        } else {
            vm.setDescription(instance.getName());
        }
        vm.setProviderOwnerId(provider.getContext().getAccountNumber());

        VmState vmState = null;
        if (instance.getStatus().equalsIgnoreCase("provisioning") || 
            instance.getStatus().equalsIgnoreCase("staging")) {
            if ((null != instance.getStatusMessage()) && (instance.getStatusMessage().contains("failed"))) {
                vmState = VmState.ERROR;
            } else {
                vmState = VmState.PENDING;
            }
        } else if (instance.getStatus().equalsIgnoreCase("stopping")) {
            vmState = VmState.STOPPING;
        } else if (instance.getStatus().equalsIgnoreCase("terminated")) {
            vmState = VmState.STOPPED;
        } else {
            vmState = VmState.RUNNING;
        }
        vm.setCurrentState(vmState);
        String regionId = "";
        try {
            regionId = provider.getDataCenterServices().getRegionFromZone(instance.getZone().substring(instance.getZone().lastIndexOf("/") + 1));
        }
        catch (Exception ex) {
            logger.error("An error occurred getting the region for the instance");
            return null;
        }
        vm.setProviderRegionId(regionId);
        String zone = instance.getZone();
        zone = zone.substring(zone.lastIndexOf("/") + 1);
        vm.setProviderDataCenterId(zone);

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse(instance.getCreationTimestamp(), fmt);
        vm.setCreationTimestamp(dt.toDate().getTime());

        if (instance.getDisks() != null) {
            for (AttachedDisk disk : instance.getDisks()) {
                if (disk != null && disk.getBoot() != null && disk.getBoot()) {
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
                    } catch (IOException ex) {
                        logger.error(ex.getMessage());
                        if (ex.getClass() == GoogleJsonResponseException.class) {
                            GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                            throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                        } else
                            throw new InternalException("An error occurred getting the source image of the VM");
                    }
                }
            }
        }

        String machineTypeName = instance.getMachineType().substring(instance.getMachineType().lastIndexOf("/") + 1);
        vm.setProductId(machineTypeName + "+" + zone);

        ArrayList<RawAddress> publicAddresses = new ArrayList<RawAddress>();
        ArrayList<RawAddress> privateAddresses = new ArrayList<RawAddress>();
        boolean firstPass = true;
        boolean isSet = false;
        String providerAssignedIpAddressId = "";
        for (NetworkInterface nic : instance.getNetworkInterfaces()) {
            if (firstPass) {
                vm.setProviderVlanId(nic.getNetwork().substring(nic.getNetwork().lastIndexOf("/") + 1));
                firstPass = false;
            }
            if (nic.getNetworkIP() != null) {
                privateAddresses.add(new RawAddress(nic.getNetworkIP()));
            }
            if (nic.getAccessConfigs() != null && !nic.getAccessConfigs().isEmpty()) {
                for (AccessConfig accessConfig : nic.getAccessConfigs()) {
                    if (accessConfig.getNatIP() != null) {
                        publicAddresses.add(new RawAddress(accessConfig.getNatIP()));
                        if (!isSet) {
                            try {
                                isSet = true;
                                providerAssignedIpAddressId = provider.getNetworkServices().getIpAddressSupport().getIpAddressIdFromIP(accessConfig.getNatIP(), regionId);
                            } catch(InternalException ex) {
                                /*Likely to be an ephemeral IP*/
                            }
                        }
                    }
                }
            }
        }
        vm.setPublicAddresses(publicAddresses.toArray(new RawAddress[publicAddresses.size()]));
        vm.setPrivateAddresses(privateAddresses.toArray(new RawAddress[privateAddresses.size()]));
        vm.setProviderAssignedIpAddressId(providerAssignedIpAddressId);

        vm.setRebootable(true);
        vm.setPersistent(true);
        vm.setIpForwardingAllowed(true);
        vm.setImagable(false);
        vm.setClonable(false);

        vm.setPlatform(Platform.guess(instance.getName()));
        vm.setArchitecture(Architecture.I64);

        vm.setTag("contentLink", instance.getSelfLink());

        return vm;
    }

    private VirtualMachineProduct toProduct(MachineType machineType){
        VirtualMachineProduct product = new VirtualMachineProduct();
        product.setProviderProductId(machineType.getName() + "+" + machineType.getZone());
        product.setName(machineType.getName());
        product.setDescription(machineType.getSelfLink());
        product.setCpuCount(machineType.getGuestCpus());
        product.setRamSize(new Storage<Megabyte>(machineType.getMemoryMb(), Storage.MEGABYTE));
        if (machineType.getImageSpaceGb() != null)
            product.setRootVolumeSize(new Storage<Gigabyte>(machineType.getImageSpaceGb(), Storage.GIGABYTE));
        else
            product.setRootVolumeSize(new Storage<Gigabyte>(0, Storage.GIGABYTE));  // defined at creation time by specified root volume size.
        product.setVisibleScope(VisibleScope.ACCOUNT_DATACENTER);
        return product;
    }

    // the default implementation does parallel launches and throws an exception only if it is unable to launch any virtual machines
    @Override
    public @Nonnull Iterable<String> launchMany( final @Nonnull VMLaunchOptions withLaunchOptions, final @Nonnegative int count ) throws CloudException, InternalException {
        if( count < 1 ) {
            throw new InternalException("Invalid attempt to launch less than 1 virtual machine (requested " + count + ").");
        }
        if( count == 1 ) {
            return Collections.singleton(launch(withLaunchOptions).getProviderVirtualMachineId());
        }
        final List<Future<String>> results = new ArrayList<Future<String>>();

        // windows on GCE follows same naming constraints as regular instances, 1-62 lower and numbers, must begin with a letter.
        NamingConstraints c = NamingConstraints.getAlphaNumeric(1, 63).withNoSpaces().withRegularExpression("(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)").lowerCaseOnly().constrainedBy('-');
        String baseHost = c.convertToValidName(withLaunchOptions.getHostName(), Locale.US);

        if( baseHost == null ) {
            baseHost = withLaunchOptions.getHostName();
        }
        for( int i = 1; i <= count; i++ ) {
            String hostName = c.incrementName(baseHost, i);
            String friendlyName = withLaunchOptions.getFriendlyName() + "-" + i;
            VMLaunchOptions options = withLaunchOptions.copy(hostName == null ? withLaunchOptions.getHostName() + "-" + i : hostName, friendlyName);

            results.add(launchAsync(options));
        }

        PopulatorThread<String> populator = new PopulatorThread<String>(new JiteratorPopulator<String>() {
            @Override
            public void populate( @Nonnull Jiterator<String> iterator ) throws Exception {
                List<Future<String>> original = results;
                List<Future<String>> copy = new ArrayList<Future<String>>();
                Exception exception = null;
                boolean loaded = false;

                while( !original.isEmpty() ) {
                    for( Future<String> result : original ) {
                        if( result.isDone() ) {
                            try {
                                iterator.push(result.get());
                                loaded = true;
                            } catch( Exception e ) {
                                exception = e;
                            }
                        }
                        else {
                            copy.add(result);
                        }
                    }
                    original = copy;
                    // copy has to be a new list else we'll get into concurrently modified list state
                    copy = new ArrayList<Future<String>>();
                }
                if( exception != null && !loaded ) {
                    throw exception;
                }
            }
        });

        populator.populate();
        return populator.getResult();
    }

}
