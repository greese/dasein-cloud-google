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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.compute.VMLaunchOptions.NICConfig;
import org.dasein.cloud.compute.VMLaunchOptions.VolumeAttachment;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleMethod.Param;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.NICCreateOptions;
import org.dasein.cloud.network.NetworkServices;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.VLANSupport;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    }

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public VirtualMachine alterVirtualMachine(String vmId,
			VMScalingOptions options) throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public VirtualMachine clone(String vmId, String intoDcId, String name,
			String description, boolean powerOn, String... firewallIds)
					throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public VMScalingCapabilities describeVerticalScalingCapabilities()
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void disableAnalytics(String vmId) throws InternalException,
	CloudException {
		// TODO Auto-generated method stub

	}

	@Override
	public void enableAnalytics(String vmId) throws InternalException,
	CloudException {
		// TODO Auto-generated method stub

	}

    public Iterable<VmState> getTerminateVMStates(@Nullable VirtualMachine vm) {
        return Collections.emptyList();
        //TODO
    }

    @Override
    public String getPassword(@Nonnull String vmId) throws InternalException, CloudException {
        return "";
        //TODO
    }

	@Override
	public String getConsoleOutput(String vmId) throws InternalException,
	CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getCostFactor(VmState state) throws InternalException,
	CloudException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaximumVirtualMachineCount() throws CloudException,
	InternalException {
		return -2;
	}

	@Override
	public VirtualMachineProduct getProduct(String productId)
			throws InternalException, CloudException {
		for( VirtualMachineProduct product : listProducts(Architecture.I32) ) {
			if( productId.equals(product.getProviderProductId()) ) {
				return product;
			}
		}

		return null;
	}

	@Override
	public String getProviderTermForServer(Locale locale) {
		return "instance";
	}

	@Override
	public VirtualMachine getVirtualMachine(String vmId)
			throws InternalException, CloudException {

		GoogleMethod method = new GoogleMethod(provider);
		vmId = vmId.replace(" ", "").replace("-", "").replace(":", "");

		JSONArray list = method.get(GoogleMethod.SERVER  + "/" + vmId);

		if( list == null ) {
			return null;
		}

		for( int i=0; i<list.length(); i++ ) {
			try {
				VirtualMachine vm = toServer(list.getJSONObject(i));
				if( vm != null && vm.getProviderVirtualMachineId().equals(vmId) ) {
					return vm;
				}
			}
			catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}
		return null;

	}


	public @Nullable Iterable<String> getVirtualMachineWithVolume(String volumeId)
			throws InternalException, CloudException {

		GoogleMethod method = new GoogleMethod(provider);
		volumeId = volumeId.replace(" ", "").replace("-", "").replace(":", "");

		JSONArray list = method.get(GoogleMethod.SERVER);

		List<String> vmNames = new ArrayList<String>();

		if( list == null ) {
			return null;
		}

		for( int i=0; i<list.length(); i++ ) {
			try {
				JSONObject vmObject = list.getJSONObject(i);
				if (vmObject.has("disks")) {
					JSONArray diskArray = vmObject.getJSONArray("disks");
					for (int j = 0; j < diskArray.length(); j++) {
						JSONObject disk = diskArray.getJSONObject(j);
						if (disk.has("source"))  {
							String diskId = GoogleMethod.getResourceName(disk.getString("source"), GoogleMethod.VOLUME);
							if (diskId.equals(volumeId))
								vmNames.add(vmObject.getString("name"));
						}
					}
				}
			}
			catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}

		return vmNames.size() > 0 ? vmNames : null;

	}

	private @Nonnull VmState toState(String state) {
		if (state.equals("RUNNING") )  return VmState.RUNNING;
		if (state.equals("PROVISIONING") )  return VmState.PENDING;
		if (state.equals("STOPPED") )  return VmState.STOPPED;
		if (state.equals("STAGING") )  return VmState.PENDING;
		if (state.equals("TERMINATED") )  return VmState.STOPPED;

		if (logger.isDebugEnabled())
			logger.warn("DEBUG: Unknown virtual machine state: " + state);

		return VmState.PENDING;
	}

	private @Nullable VirtualMachine toServer(@Nullable JSONObject json) throws CloudException, InternalException {

		if( json == null ) {
			return null;
		}

		VirtualMachine vm = new VirtualMachine();

		vm.setCurrentState(VmState.PENDING);
		vm.setProviderOwnerId(getContext().getAccountNumber());
		vm.setProviderRegionId(getContext().getRegionId());
		vm.setProviderSubnetId(null);
		vm.setProviderVlanId(null);
		vm.setImagable(false);
		vm.setProviderDataCenterId(vm.getProviderRegionId() + "-a");
		vm.setPlatform(Platform.UNKNOWN);

		// Always the architecture is set to I32
		vm.setArchitecture(Architecture.I32);
		vm.setPersistent(true);
		vm.setRebootable(false);

		try {
			if( json.has("name") ) {
				vm.setProviderVirtualMachineId(json.getString("name"));
				vm.setName(json.getString("name"));
			}

			if( json.has("description") ) {
				vm.setDescription(json.getString("description"));
			}
			if( json.has("zone") ) {
				String zoneUrl = (String) json.get("zone");

				String zone = GoogleMethod.getResourceName(zoneUrl, GoogleMethod.ZONE);
				vm.setProviderDataCenterId(zone);
			}
			if( json.has("status") ) {
				String status = (String) json.get("status");
				vm.setCurrentState(toState(status));
				if( vm.getCurrentState().equals(VmState.RUNNING) ) {
					vm.setRebootable(true);
				}
			}
			if( json.has("image") ) {
				String os = (String) json.get("image");
				os = GoogleMethod.getResourceName(os, GoogleMethod.IMAGE);
				vm.setProviderMachineImageId(os);
				vm.setPlatform(Platform.guess(os));
			}

			if (json.has("networkInterfaces")) {
				JSONArray networkInterfaces = json.getJSONArray("networkInterfaces");

				JSONObject networkInterface = networkInterfaces.getJSONObject(0);

				if (json.has("network")) {
					String networkUrl = (String) json.get("network");
					String network = GoogleMethod.getResourceName(networkUrl, GoogleMethod.NETWORK);
					vm.setProviderVlanId(network);
				}

				if (networkInterface.has("networkIP")) {
					String ip = (String) networkInterface.get("networkIP");
					vm.setProviderAssignedIpAddressId(ip);
				}

				if (networkInterface.has("accessConfigs")) {
					JSONArray accessConfigs = networkInterface.getJSONArray("accessConfigs");
					List<String> addr = new ArrayList<String>();
					for (int i = 0; i < accessConfigs.length(); i++) {
						JSONObject accessConfig = accessConfigs.getJSONObject(i);
						if (accessConfig.has("natIP")) {
							addr.add((String) accessConfig.get("natIP"));
						}
					}
					vm.setPublicIpAddresses((String[]) addr.toArray());	
				}
			}

			if(json.has("creationTimestamp") ) {
				SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
				String value = json.getString("creationTimestamp");
				try {
					vm.setCreationTimestamp(fmt.parse(value).getTime());
				} catch (java.text.ParseException e) {
					logger.error(e);
					e.printStackTrace();
					throw new CloudException(e);
				}				
			}
			if (json.has("machineType")) {
				String product = json.getString("machineType");
				product = GoogleMethod.getResourceName(product, GoogleMethod.MACHINE_TYPE);
				vm.setProductId(product);
			}
		}
		catch( JSONException e ) {
			logger.error("Failed to parse JSON from the cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}
		if( vm.getProviderVirtualMachineId() == null ) {
			logger.warn("Object had no ID: " + json);
			return null;
		}
		if( vm.getName() == null ) {
			vm.setName(vm.getProviderVirtualMachineId());
		}
		if( vm.getDescription() == null ) {
			vm.setDescription(vm.getName());
		}
		vm.setClonable(false);
		vm.setImagable(false);
		return vm;
	}


	@Override
	public VmStatistics getVMStatistics(String vmId, long from, long to)
			throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<VmStatistics> getVMStatisticsForPeriod(String vmId,
			long from, long to) throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Requirement identifyImageRequirement(ImageClass cls)
			throws CloudException, InternalException {
		return (cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE);
	}

	@Override
	public Requirement identifyPasswordRequirement() throws CloudException,
	InternalException {
		return Requirement.NONE;
	}

	@Override
	public Requirement identifyPasswordRequirement(Platform platform)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Requirement identifyRootVolumeRequirement() throws CloudException,
	InternalException {
		// TODO Auto-generated method stub
		return Requirement.NONE;
	}

	@Override
	public Requirement identifyShellKeyRequirement() throws CloudException,
	InternalException {
		return Requirement.NONE;
	}

	@Override
	public Requirement identifyShellKeyRequirement(Platform platform)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Requirement identifyStaticIPRequirement() throws CloudException,
	InternalException {
		return Requirement.NONE;
	}

	@Override
	public Requirement identifyVlanRequirement() throws CloudException,
	InternalException {
		return Requirement.NONE;
	}

	@Override
	public boolean isAPITerminationPreventable() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean isBasicAnalyticsSupported() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean isExtendedAnalyticsSupported() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isUserDataSupported() throws CloudException,
	InternalException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public @Nonnull VirtualMachine launch(VMLaunchOptions withLaunchOptions)
			throws CloudException, InternalException {

		GoogleMethod method = new GoogleMethod(provider);

		if( logger.isDebugEnabled() ) {
			logger.debug("Launching VM: " + withLaunchOptions.getHostName());
		}

		ProviderContext ctx = provider.getContext();

		JSONObject payload = new JSONObject();
		try {

			String vmname = withLaunchOptions.getHostName().toLowerCase();
			vmname = vmname.replace(" ", "").replace("-", "").replace(":", "");
			payload.put("name", vmname);

			if ( withLaunchOptions.getMachineImageId() != null) {
				String image = method.getEndpoint(ctx, GoogleMethod.IMAGE) + "/" +  withLaunchOptions.getMachineImageId();
				payload.put("image", image);
			}

			if( withLaunchOptions.getStandardProductId() != null) {
				String machineType = method.getEndpoint(ctx, GoogleMethod.MACHINE_TYPE) + "/" + withLaunchOptions.getStandardProductId();
				payload.put("machineType", machineType);
			}

			String zone = ctx.getRegionId() + "-a";
			if(withLaunchOptions.getDataCenterId() != null) {
				zone = withLaunchOptions.getDataCenterId();
			} 
			payload.put("zone", method.getEndpoint(ctx, GoogleMethod.ZONE) + "/" + zone);


			String vlanId = "default";

			if(withLaunchOptions.getNetworkInterfaces() != null) {
				NICConfig[] nicConfigs = withLaunchOptions.getNetworkInterfaces();
				for (NICConfig nicConfig: nicConfigs ) {
					NICCreateOptions createOpts = nicConfig.nicToCreate;
					String staticIp =  createOpts.getIpAddress();

					JSONArray networkConfigArray = new JSONArray();
					JSONObject networkObj = new JSONObject();
					networkObj.put("name", nicConfig.nicId);
					networkObj.append("network", method.getEndpoint(ctx, GoogleMethod.NETWORK) + "/" + createOpts.getVlanId());

					if (staticIp != null) {
						JSONArray accessConfigArray = new JSONArray();
						JSONObject accessConfig = new JSONObject();
						accessConfig.put("kind", "compute#accessConfig");
						accessConfig.put("name", createOpts.getName());
						accessConfig.put("type", "ONE_TO_ONE_NAT");
						accessConfig.put("natIP", staticIp);
						accessConfigArray.put(accessConfig);

						networkObj.put("accessConfigs", accessConfigArray);
					}
					networkConfigArray.put(networkObj);
					payload.put("networkInterfaces", networkConfigArray);

				}
			} else if(withLaunchOptions.getVlanId() != null) {
				vlanId =  withLaunchOptions.getVlanId();

				JSONArray networkConfigArray = new JSONArray();
				JSONObject networkObj = new JSONObject();
				networkObj.put("name", vlanId);
				networkObj.append("network", method.getEndpoint(ctx, GoogleMethod.NETWORK) + "/" + vlanId);

				if (withLaunchOptions.getStaticIpIds() != null) {
					String[] staticIps = withLaunchOptions.getStaticIpIds();
					JSONArray accessConfigArray = new JSONArray();
					for (String ip : staticIps) {
						JSONObject accessConfig = new JSONObject();
						accessConfig.put("kind", "compute#accessConfig");
						accessConfig.put("name", ip);
						accessConfig.put("type", "ONE_TO_ONE_NAT");
						accessConfig.put("natIP", ip);
						accessConfigArray.put(accessConfig);
					}
					networkObj.put("accessConfigs", accessConfigArray);
				}
				networkConfigArray.put(networkObj);
				payload.put("networkInterfaces", networkConfigArray);

			} else {
				JSONArray networkConfigArray = new JSONArray();
				JSONObject networkObj = new JSONObject();
				networkObj.put("network", method.getEndpoint(ctx, GoogleMethod.NETWORK) + "/" + vlanId);
				networkConfigArray.put(networkObj);
				payload.put("networkInterfaces", networkConfigArray);
			}

			if(withLaunchOptions.getKernelId() != null) {
				String kernel = method.getEndpoint(ctx, GoogleMethod.KERNEL) + "/" +  withLaunchOptions.getKernelId();
				payload.put("kernel", kernel);
			}

			if(withLaunchOptions.getMetaData() != null) {
				Map<String, Object> metaData = withLaunchOptions.getMetaData();
				JSONArray items = new JSONArray();
				for (Map.Entry<String, Object> entry : metaData.entrySet()) {
					JSONObject item = new JSONObject();
					item.put("key", entry.getKey());
					item.put("value", entry.getValue().toString());
					items.put(item);
				}

				metaData.put("kind", "compute#metadata");
				metaData.put("items", items);
				payload.put("metadata", metaData);
			}

			if(withLaunchOptions.getVolumes() != null) {
				JSONArray diskArray = new JSONArray();

				VolumeAttachment[] attachments = withLaunchOptions.getVolumes();
				for (VolumeAttachment attachment : attachments) {

					JSONObject diskObj = new JSONObject();

					VolumeCreateOptions createOptions = attachment.volumeToCreate;

					if (createOptions != null) {
						// ephemeral
						diskObj.put("type", "EPHEMERAL");
						diskObj.put("mode", "READ_WRITE");
						if (createOptions.getName() != null) diskObj.put("deviceName",createOptions.getName());
					} else {
						// persistent disk
						diskObj.put("type", "PERSISTENT");
						diskObj.put("mode", "READ_WRITE");
						String volume = method.getEndpoint(ctx, GoogleMethod.VOLUME) + "/" +  attachment.existingVolumeId;
						diskObj.put("source", volume);
						if (attachment.deviceId != null) diskObj.put("deviceName", attachment.deviceId);
					}

					diskArray.put(diskObj);
				}

				payload.put("disks", diskArray);
			}

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error while constructing the VM launch options");
			throw new CloudException(e);
		}

		if( logger.isDebugEnabled() ) {
			logger.debug("json payload =" + payload);
		}

		JSONObject launches = method.post(GoogleMethod.SERVER, payload);

		if( logger.isDebugEnabled() ) {
			logger.debug("json reponse =" + launches.toString());
		}

		if( logger.isDebugEnabled() ) {
			logger.debug("launch list=" + launches);
			if( launches != null ) {
				logger.debug("size=" + launches.length());
			}
		}

		String vmName = null;
		String status = method.getOperationStatus(GoogleMethod.OPERATION, launches);
		if (status != null && status.equals("DONE")) {
			if( launches.has("targetLink") ) {
				try {
					vmName = launches.getString("targetLink");
				} catch (JSONException e) {
					e.printStackTrace();
					logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
					throw new CloudException(e);
				}

				vmName = GoogleMethod.getResourceName(vmName, GoogleMethod.SERVER);
				return getVirtualMachine(vmName);
			}
		}
		throw new CloudException("No servers were returned from the server as a result of the launch");
	}

	@Override
	public VirtualMachine launch(String fromMachineImageId,
			VirtualMachineProduct product, String dataCenterId, String name,
			String description, String withKeypairId, String inVlanId,
			boolean withAnalytics, boolean asSandbox, String... firewallIds)
					throws InternalException, CloudException {
		return launch(fromMachineImageId, product, dataCenterId, name, description, withKeypairId, inVlanId, withAnalytics, asSandbox, firewallIds, new Tag[0]);
	}

	@Override
	public VirtualMachine launch(String fromMachineImageId,
			VirtualMachineProduct product, String dataCenterId, String name,
			String description, String withKeypairId, String inVlanId,
			boolean withAnalytics, boolean asSandbox, String[] firewallIds,
			Tag... tags) throws InternalException, CloudException {
		VMLaunchOptions cfg = VMLaunchOptions.getInstance(product.getProviderProductId(), fromMachineImageId, name, description);


		if( withKeypairId != null ) {
			cfg.withBoostrapKey(withKeypairId);
		}
		if( inVlanId != null ) {
			NetworkServices svc = provider.getNetworkServices();

			if( svc != null ) {
				VLANSupport support = svc.getVlanSupport();

				if( support != null ) {
					Subnet subnet = support.getSubnet(inVlanId);

					if( subnet == null ) {
						throw new CloudException("No such VPC subnet: " + inVlanId);
					}
					dataCenterId = subnet.getProviderDataCenterId();
				}
			}
			cfg.inVlan(null, dataCenterId, inVlanId);
		}
		else {
			cfg.inDataCenter(dataCenterId);
		}
		if( withAnalytics ) {
			cfg.withExtendedAnalytics();
		}
		if( firewallIds != null && firewallIds.length > 0 ) {
			cfg.behindFirewalls(firewallIds);
		}
		if( tags != null && tags.length > 0 ) {
			HashMap<String,Object> meta = new HashMap<String, Object>();

			for( Tag t : tags ) {
				meta.put(t.getKey(), t.getValue());
			}
			cfg.withMetaData(meta);
		}
		return launch(cfg);
	}

	@Override
	public Iterable<String> listFirewalls(String vmId)
			throws InternalException, CloudException {
		return Collections.emptyList();
	}

	static private HashMap<String,Map<Architecture,Collection<VirtualMachineProduct>>> productCache;

	@Override
	public Iterable<VirtualMachineProduct> listProducts(
			Architecture architecture) throws InternalException, CloudException {

		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new CloudException("No region was set for this request");
		}
		if( productCache != null ) {
			Map<Architecture,Collection<VirtualMachineProduct>> cached = productCache.get(ctx.getEndpoint());

			if( cached != null ) {
				Collection<VirtualMachineProduct> c = cached.get(architecture);

				if( c == null ) {
					return Collections.emptyList();
				}
				return c;
			}
		}
		GoogleMethod method = new GoogleMethod(provider);

		JSONArray list = method.get(GoogleMethod.MACHINE_TYPE);

		if( list == null ) {
			return Collections.emptyList();
		}

		ArrayList<VirtualMachineProduct> products = new ArrayList<VirtualMachineProduct>();

		for( int i=0; i<list.length(); i++ ) {
			try {
				VirtualMachineProduct prd = toProduct(list.getJSONObject(i));

				if( prd != null ) {
					products.add(prd);
				}
			}
			catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}
		HashMap<Architecture,Collection<VirtualMachineProduct>> map = new HashMap<Architecture, Collection<VirtualMachineProduct>>();

		map.put(Architecture.I32, Collections.unmodifiableList(products)); 
		map.put(Architecture.I64, Collections.unmodifiableList(products));
		if( productCache == null ) {
			HashMap<String,Map<Architecture,Collection<VirtualMachineProduct>>> pm = new HashMap<String, Map<Architecture, Collection<VirtualMachineProduct>>>();

			pm.put(ctx.getEndpoint(), map);
			productCache = pm;
		}
		return products;


	}
	private @Nullable VirtualMachineProduct toProduct(@Nullable JSONObject json) throws CloudException, InternalException {

		if( json == null ) {
			return null;
		}

		VirtualMachineProduct product = new VirtualMachineProduct();

		// TODO: Check if the json output from the server has ephemeralDisks & the diskGb is 10
		product.setRootVolumeSize(new Storage<Gigabyte>(10, Storage.GIGABYTE));
		try {
			if( json.has("id") ) {
				product.setProviderProductId(json.getString("id"));
			}
			if( json.has("guestCpus") ) {
				product.setCpuCount(Integer.parseInt(json.getString("guestCpus")));
			} else product.setCpuCount(1);

			if( json.has("name") ) {
				product.setName(json.getString("name"));
			}

			try {
				if( json.has("memoryMb") ) {
					product.setRamSize(Storage.valueOf(json.getString("memoryMb")));
				}
			}
			catch( Throwable ignore ) {
				product.setRamSize(new Storage<Gigabyte>(1, Storage.GIGABYTE));
			}

			if( json.has("description") ) {
				product.setDescription(json.getString("description"));
			}
			// TODO: Maximum persistent disks maximumPersistentDisks(16)  & maximumPersistentDisksSizeGB limits in GCE are not set to the products (Vinothini)
		}
		catch( JSONException e ) {
			logger.error("Invalid JSON from cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}
		if( product.getProviderProductId() == null ) {
			return null;
		}
		if( product.getName() == null ) {
			product.setName(product.getProviderProductId());
		}
		if( product.getDescription() == null ) {
			product.setDescription(product.getName());
		}
		return product;
	}

	static private Collection<Architecture> architectures;
	@Override
	public Iterable<Architecture> listSupportedArchitectures()
			throws InternalException, CloudException {
		if( architectures == null ) {
			ArrayList<Architecture> tmp = new ArrayList<Architecture>();

			tmp.add(Architecture.I32);
			tmp.add(Architecture.I64);
			architectures = Collections.unmodifiableList(tmp);
		}
		return architectures;
	}

	private @Nullable ResourceStatus toStatus(@Nonnull JSONObject json) throws CloudException, InternalException {
		VmState state = VmState.PENDING;
		String id = null;

		try {
			if( json.has("name") ) {
				id = json.getString("serverId");
			}
			if( json.has("status") ) {
				state = toState(json.getString("status"));
			}
		}
		catch( JSONException e ) {
			logger.error("Invalid JSON from enStratus: " + e.getMessage());
			throw new CloudException(e);
		}
		if( id == null ) {
			return null;
		}
		return new ResourceStatus(id, state);
	}

	@Override
	public Iterable<ResourceStatus> listVirtualMachineStatus()
			throws InternalException, CloudException {

		ArrayList<ResourceStatus> vmStatus = new ArrayList<ResourceStatus>();

		GoogleMethod method = new GoogleMethod(provider);

		JSONArray list = method.get(GoogleMethod.SERVER);
		if( list == null ) {
			return Collections.emptyList();
		}
		for( int i=0; i<list.length(); i++ ) {
			try {

				JSONObject json = list.getJSONObject(i);
				if (json.has("status")) {
					ResourceStatus status = toStatus(json);
					vmStatus.add(status);
				}

			} catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}

		return vmStatus;
	}

	@Override
	public Iterable<VirtualMachine> listVirtualMachines()
			throws InternalException, CloudException {
		GoogleMethod method = new GoogleMethod(provider);

		JSONArray list = method.get(GoogleMethod.SERVER);
		if( list == null ) {
			return Collections.emptyList();
		}
		ArrayList<VirtualMachine> servers = new ArrayList<VirtualMachine>();

		for( int i=0; i<list.length(); i++ ) {
			try {

				VirtualMachine vm = toServer(list.getJSONObject(i));

				if( vm != null ) servers.add(vm);

			} catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}

		return servers;
	}

	@Override
	public Iterable<VirtualMachine> listVirtualMachines(VMFilterOptions options)
			throws InternalException, CloudException {
		GoogleMethod method = new GoogleMethod(provider);

		Param param = new Param("filter", options.getRegex());

		JSONArray list = method.get(GoogleMethod.SERVER, param );
		if( list == null ) {
			return Collections.emptyList();
		}
		ArrayList<VirtualMachine> servers = new ArrayList<VirtualMachine>();

		for( int i=0; i<list.length(); i++ ) {
			try {

				VirtualMachine vm = toServer(list.getJSONObject(i));

				if( vm != null ) servers.add(vm);

			} catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}

		return servers;
	}

	@Override
	public void pause(String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support pausing vms");
	}

	@Override
	public void reboot(String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support rebooting vms");
	}

	@Override
	public void resume(String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support resuming vms");
	}

	@Override
	public void start(String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support starting vms");
	}

	@Override
	public void stop(String vmId, boolean force) throws InternalException,
	CloudException {
		throw new OperationNotSupportedException("Google does not support stopping vms");

	}

	@Override
	public boolean supportsPauseUnpause(VirtualMachine vm) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsStartStop(VirtualMachine vm) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsSuspendResume(VirtualMachine vm) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void suspend(String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support suspending vms");
	}

	@Override
	public void terminate(String vmId) throws InternalException, CloudException {
		GoogleMethod method = new GoogleMethod(provider);

		method.delete(GoogleMethod.SERVER, new GoogleMethod.Param("id", vmId));
		long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 15L);

		while( timeout > System.currentTimeMillis() ) {
			VirtualMachine vm = getVirtualMachine(vmId);

			if( vm == null || vm.getCurrentState().equals(VmState.TERMINATED) ) {
				return;
			}
			try { Thread.sleep(15000L); }
			catch( InterruptedException ignore ) { }
		}
		throw new CloudException("VM termination failed !");
	}

    @Override
    public void terminate(String vmId, String reason) throws InternalException, CloudException{
        terminate(vmId);
    }

	@Override
	public void unpause(String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support unpausing vms");

	}

	@Override
	public void updateTags(String vmId, Tag... tags) throws CloudException,
	InternalException {
		updateTags(new String[]{vmId}, tags);

	}

	@Override
	public void updateTags(String[] vmIds, Tag... tags) throws CloudException,
	InternalException {
		GoogleMethod method = new GoogleMethod(provider);

		JSONObject jsonPayload = null;
		for(String vmId: vmIds) {
			try {
				vmId = vmId.replace(" ", "").replace("-", "").replace(":", "");
				//			VirtualMachine vm = getVirtualMachine(vmId);
				JSONObject metaData = new JSONObject();
				JSONArray items = new JSONArray();
				jsonPayload = new JSONObject();
				for (Tag tag: tags) {
					JSONObject item = new JSONObject();
					item.put("key", tag.getKey());
					item.put("value", tag.getValue());
					items.put(item);
				}

				metaData.put("kind", "compute#metadata");
				metaData.put("items", items);
				jsonPayload.put("metadata", metaData);
				if( logger.isDebugEnabled() ) {
					logger.debug("json payload =" + jsonPayload);
				}
			} catch (JSONException e) {
				e.printStackTrace();
				logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
				throw new CloudException(e);
			}

			JSONObject patchedResponse = method.patch(GoogleMethod.SERVER + "/" + vmId, jsonPayload);

			if( logger.isDebugEnabled() ) {
				logger.debug("json reponse =" + patchedResponse.toString());
			}

			String vmName = null;
			String status = method.getOperationStatus(GoogleMethod.OPERATION, patchedResponse);
			if (status != null && status.equals("DONE")) {
				if( patchedResponse.has("targetLink") ) {
					try {
						vmName = patchedResponse.getString("targetLink");
					} catch (JSONException e) {
						e.printStackTrace();
						logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
						throw new CloudException(e);
					}
				}
			}
			throw new CloudException("No servers were returned from the server as a result of the launch");
		}
	}

	@Override
	public void removeTags(String vmId, Tag... tags) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("Google does not support removing meta data from vms");

	}

	@Override
	public void removeTags(String[] vmIds, Tag... tags) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("Google does not support removing meta data from vms");

	}

}
