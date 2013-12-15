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

import com.google.api.client.util.Maps;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.compute.VMLaunchOptions.NICConfig;
import org.dasein.cloud.compute.VMLaunchOptions.VolumeAttachment;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.NoContextException;
import org.dasein.cloud.google.util.ExceptionUtils;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.model.GoogleInstances;
import org.dasein.cloud.google.util.model.GoogleMachineTypes;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.NICCreateOptions;
import org.dasein.cloud.network.NetworkServices;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.VLANSupport;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * Implements the compute services supported in the Google API.
 *
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleServerSupport extends AbstractVMSupport<Google> {

	private static final Logger logger = Google.getLogger(GoogleServerSupport.class);

	private static final Collection<Architecture> SUPPORTED_ARCHITECTURES = ImmutableSet.of(Architecture.I32, Architecture.I64);

	private Google provider;

	public GoogleServerSupport(Google cloud) {
		super(cloud);
		this.provider = cloud;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public VirtualMachine alterVirtualMachine(String vmId, VMScalingOptions options) throws InternalException, CloudException {
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
	public VirtualMachineProduct getProduct(String productId) throws InternalException, CloudException {
		for (VirtualMachineProduct product : listProducts(Architecture.I64)) {
			if (productId.equals(product.getProviderProductId())) {
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
	@Nullable
	public VirtualMachine getVirtualMachine(String virtualMachineId) throws InternalException, CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		ProviderContext context = provider.getContext();
		Instance googleInstance = findInstance(virtualMachineId, context.getAccountNumber(), context.getRegionId());
		return googleInstance != null ? GoogleInstances.toDaseinVirtualMachine(googleInstance, provider.getContext()) : null;
	}

	/**
	 * Google doesn't provide method to fetch instances by Region only by DataCenter, therefore attempt to find disk in each zone of current
	 * region
	 */
	@Nullable
	protected Instance findInstance(String instanceId, String projectId, String regionId) throws InternalException, CloudException {
		Iterable<DataCenter> dataCentersInRegion = provider.getDataCenterServices().listDataCenters(regionId);
		for (DataCenter dataCenter : dataCentersInRegion) {
			Instance instance = findInstanceInZone(instanceId, projectId, dataCenter.getName());
			if (instance != null) {
				return instance;
			}
		}
		return null;
	}

	@Nullable
	protected Instance findInstanceInZone(String instanceId, String projectId, String zoneId) throws CloudException {
		Compute compute = provider.getGoogleCompute();
		try {
			Compute.Instances.Get getInstanceRequest = compute.instances().get(projectId, zoneId, instanceId);
			Instance googleInstance = getInstanceRequest.execute();
			if (googleInstance != null) {
				return googleInstance;
			}
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}
		return null;
	}

	@Nullable
	public Iterable<String> getVirtualMachinesWithVolume(String volumeId) throws InternalException, CloudException {
		Preconditions.checkNotNull(volumeId);

		List<String> vmNames = new ArrayList<String>();

		// Currently google doesn't support filters by embedded objects like disks,
		// therefore loop through all elements
		Iterable<Instance> instances = listInstances(VMFilterOptions.getInstance(), GoogleInstances.IdentityFunction);
		for (Instance googleInstance : instances) {
			for (AttachedDisk disk : googleInstance.getDisks()) {
				if (volumeId.equals(GoogleEndpoint.VOLUME.getResourceFromUrl(disk.getSource()))) {
					vmNames.add(googleInstance.getName());
				}
			}
		}

		return vmNames;
	}

	@Override
	public VmStatistics getVMStatistics(String vmId, long from, long to) throws InternalException, CloudException {
		return null;
	}

	@Override
	public Iterable<VmStatistics> getVMStatisticsForPeriod(String vmId, long from, long to) throws InternalException, CloudException {
		return null;
	}

	@Override
	public Requirement identifyImageRequirement(ImageClass cls) throws CloudException, InternalException {
		return (cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE);
	}

	@Override
	public Requirement identifyPasswordRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public Requirement identifyPasswordRequirement(Platform platform)
			throws CloudException, InternalException {
		return null;
	}

	@Override
	public Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public Requirement identifyShellKeyRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public Requirement identifyShellKeyRequirement(Platform platform) throws CloudException, InternalException {
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
	public boolean isUserDataSupported() throws CloudException, InternalException {
		return false;
	}

	@Override
	@Nonnull
	public VirtualMachine launch(VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {

		GoogleMethod method = new GoogleMethod(provider);

		if (logger.isDebugEnabled()) {
			logger.debug("Launching VM: " + withLaunchOptions.getHostName());
		}

		ProviderContext ctx = provider.getContext();

		JSONObject payload = new JSONObject();
		try {

			payload.put("name", withLaunchOptions.getHostName());

			if (withLaunchOptions.getMachineImageId() != null) {
				String image = method.getEndpoint(ctx, GoogleMethod.IMAGE) + "/" + withLaunchOptions.getMachineImageId();
				payload.put("image", image);
			}

			if (withLaunchOptions.getStandardProductId() != null) {
				String machineType = method.getEndpoint(ctx, GoogleMethod.MACHINE_TYPE) + "/" + withLaunchOptions.getStandardProductId();
				payload.put("machineType", machineType);
			}

			String zone = ctx.getRegionId() + "-a";
			if (withLaunchOptions.getDataCenterId() != null) {
				zone = withLaunchOptions.getDataCenterId();
			}
			payload.put("zone", method.getEndpoint(ctx, GoogleMethod.ZONE) + "/" + zone);


			String vlanId = "default";

			if (withLaunchOptions.getNetworkInterfaces() != null) {
				NICConfig[] nicConfigs = withLaunchOptions.getNetworkInterfaces();
				for (NICConfig nicConfig : nicConfigs) {
					NICCreateOptions createOpts = nicConfig.nicToCreate;
					String staticIp = createOpts.getIpAddress();

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
			} else if (withLaunchOptions.getVlanId() != null) {
				vlanId = withLaunchOptions.getVlanId();

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

			if (withLaunchOptions.getKernelId() != null) {
				String kernel = method.getEndpoint(ctx, GoogleMethod.KERNEL) + "/" + withLaunchOptions.getKernelId();
				payload.put("kernel", kernel);
			}

			if (withLaunchOptions.getMetaData() != null) {
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

			if (withLaunchOptions.getVolumes() != null) {
				JSONArray diskArray = new JSONArray();

				VolumeAttachment[] attachments = withLaunchOptions.getVolumes();
				for (VolumeAttachment attachment : attachments) {

					JSONObject diskObj = new JSONObject();

					VolumeCreateOptions createOptions = attachment.volumeToCreate;

					if (createOptions != null) {
						// ephemeral
						diskObj.put("type", "EPHEMERAL");
						diskObj.put("mode", "READ_WRITE");
						if (createOptions.getName() != null) diskObj.put("deviceName", createOptions.getName());
					} else {
						// persistent disk
						diskObj.put("type", "PERSISTENT");
						diskObj.put("mode", "READ_WRITE");
						String volume = method.getEndpoint(ctx, GoogleMethod.VOLUME) + "/" + attachment.existingVolumeId;
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

		if (logger.isDebugEnabled()) {
			logger.debug("json payload =" + payload);
		}

		JSONObject launches = method.post(GoogleMethod.SERVER, payload);

		if (logger.isDebugEnabled()) {
			logger.debug("json reponse =" + launches.toString());
		}

		if (logger.isDebugEnabled()) {
			logger.debug("launch list=" + launches);
			if (launches != null) {
				logger.debug("size=" + launches.length());
			}
		}

		String vmName = null;
		String status = method.getOperationStatus(GoogleMethod.OPERATION, launches);
		if (status != null && status.equals("DONE")) {
			if (launches.has("targetLink")) {
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


		if (withKeypairId != null) {
			cfg.withBoostrapKey(withKeypairId);
		}
		if (inVlanId != null) {
			NetworkServices svc = provider.getNetworkServices();

			if (svc != null) {
				VLANSupport support = svc.getVlanSupport();

				if (support != null) {
					Subnet subnet = support.getSubnet(inVlanId);

					if (subnet == null) {
						throw new CloudException("No such VPC subnet: " + inVlanId);
					}
					dataCenterId = subnet.getProviderDataCenterId();
				}
			}
			cfg.inVlan(null, dataCenterId, inVlanId);
		} else {
			cfg.inDataCenter(dataCenterId);
		}
		if (withAnalytics) {
			cfg.withExtendedAnalytics();
		}
		if (firewallIds != null && firewallIds.length > 0) {
			cfg.behindFirewalls(firewallIds);
		}
		if (tags != null && tags.length > 0) {
			HashMap<String, Object> meta = new HashMap<String, Object>();

			for (Tag t : tags) {
				meta.put(t.getKey(), t.getValue());
			}
			cfg.withMetaData(meta);
		}
		return launch(cfg);
	}

	@Override
	public Iterable<String> listFirewalls(String vmId) throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<VirtualMachineProduct> listProducts(Architecture architecture) throws InternalException, CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		ProviderContext context = provider.getContext();

		// load from cache if possible
		String cacheKey = getMachineTypesRegionKey(context.getAccountNumber(), context.getRegionId(), architecture);
		Cache<VirtualMachineProduct> cache = Cache.getInstance(provider, cacheKey, VirtualMachineProduct.class, CacheLevel.CLOUD_ACCOUNT,
				new TimePeriod<Hour>(1, TimePeriod.HOUR));
		Collection<VirtualMachineProduct> cachedProducts = (Collection<VirtualMachineProduct>) cache.get(context);
		if (cachedProducts != null) {
			return cachedProducts;
		}

		Compute compute = provider.getGoogleCompute();

		/*
		 * For some reason google requires to specify zone for machine types, but in the same time
		 * they look completely the same in each zone and not even distinguished in GCE console
		 * TODO: clarify how to handle machine types per zone, for now return a unique set of machine types per region
		 */
		Map<String, VirtualMachineProduct> products = Maps.newHashMap();
		Iterable<DataCenter> dataCenters = provider.getDataCenterServices().listDataCenters(context.getRegionId());
		for (DataCenter dataCenter : dataCenters) {
			try {
				Compute.MachineTypes.List listMachineTypesRequest = compute.machineTypes()
						.list(context.getAccountNumber(), dataCenter.getName());
				MachineTypeList machineTypeList = listMachineTypesRequest.execute();
				if (machineTypeList.getItems() != null) {
					for (MachineType machineType : machineTypeList.getItems()) {
						products.put(machineType.getName(), GoogleMachineTypes.toDaseinVmProduct(machineType));
					}
				}
			} catch (IOException e) {
				ExceptionUtils.handleGoogleResponseError(e);
			}
		}

		if (!products.isEmpty()) {
			cache.put(context, products.values());
		}

		return products.values();
	}

	/**
	 * Returns machine types in region key for caching
	 *
	 * @param projectId    google project ID
	 * @param regionId     region ID
	 * @param architecture machine architecture
	 * @return string key
	 */
	private static String getMachineTypesRegionKey(String projectId, String regionId, Architecture architecture) {
		return projectId + "-" + regionId + "-" + architecture + "-products";
	}

	@Override
	public Iterable<Architecture> listSupportedArchitectures() throws InternalException, CloudException {
		return SUPPORTED_ARCHITECTURES;
	}

	@Override
	public Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
		APITrace.begin(provider, "listVirtualMachineStatus");
		try {
			return listInstances(VMFilterOptions.getInstance(),
					new GoogleInstances.InstanceToDaseinStatusConverter(provider.getContext()));
		} finally {
			APITrace.end();
		}
	}

	@Override
	public Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
		return listVirtualMachines(VMFilterOptions.getInstance());
	}

	@Override
	public Iterable<VirtualMachine> listVirtualMachines(VMFilterOptions options) throws InternalException, CloudException {
		APITrace.begin(provider, "listVirtualMachines");
		try {
			return listInstances(options, new GoogleInstances.InstanceToDaseinVMConverter(provider.getContext()));
		} finally {
			APITrace.end();
		}
	}

	protected <T> Iterable<T> listInstances(VMFilterOptions options, Function<Instance, T> instanceConverter)
			throws InternalException, CloudException {
		Preconditions.checkNotNull(options);
		Preconditions.checkNotNull(instanceConverter);

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		List<T> dasinObjects = new ArrayList<T>();

		// Google doesn't provide method to fetch instances by Region - only by DataCenter
		Iterable<DataCenter> dataCenters = provider.getDataCenterServices().listDataCenters(context.getRegionId());
		try {
			for (DataCenter dataCenter : dataCenters) {
				Compute.Instances.List listInstancesRequest = compute.instances().list(provider.getContext().getAccountNumber(),
						dataCenter.getName());
				// TODO: looks like options are always empty - check!
				listInstancesRequest.setFilter(options.getRegex());

				InstanceList instanceList = listInstancesRequest.execute();
				if (instanceList.getItems() != null) {
					for (Instance googleInstance : instanceList.getItems()) {
						dasinObjects.add(instanceConverter.apply(googleInstance));
					}
				}
			}
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		return dasinObjects;
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
	public void stop(String vmId, boolean force) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support stopping vms");
	}

	@Override
	public boolean supportsPauseUnpause(VirtualMachine vm) {
		return false;
	}

	@Override
	public boolean supportsStartStop(VirtualMachine vm) {
		return false;
	}

	@Override
	public boolean supportsSuspendResume(VirtualMachine vm) {
		return false;
	}

	@Override
	public void suspend(String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support suspending vms");
	}

	@Override
	public void terminate(@Nonnull String vmId, @Nullable String explanation) throws InternalException, CloudException {
		GoogleMethod method = new GoogleMethod(provider);

		method.delete(GoogleMethod.SERVER, new GoogleMethod.Param("id", vmId));
		long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 15L);

		while (timeout > System.currentTimeMillis()) {
			VirtualMachine vm = getVirtualMachine(vmId);

			if (vm == null || vm.getCurrentState().equals(VmState.TERMINATED)) {
				return;
			}
			try {
				Thread.sleep(15000L);
			} catch (InterruptedException ignore) {
			}
		}
		throw new CloudException("VM termination failed !");
	}

	@Override
	public void unpause(String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support unpausing vms");

	}

	@Override
	public void updateTags(String vmId, Tag... tags) throws CloudException, InternalException {
		updateTags(new String[]{vmId}, tags);
	}

	@Override
	public void updateTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		GoogleMethod method = new GoogleMethod(provider);

		JSONObject jsonPayload = null;
		for (String vmId : vmIds) {
			try {
				//			VirtualMachine vm = getVirtualMachine(vmId);
				JSONObject metaData = new JSONObject();
				JSONArray items = new JSONArray();
				jsonPayload = new JSONObject();
				for (Tag tag : tags) {
					JSONObject item = new JSONObject();
					item.put("key", tag.getKey());
					item.put("value", tag.getValue());
					items.put(item);
				}

				metaData.put("kind", "compute#metadata");
				metaData.put("items", items);
				jsonPayload.put("metadata", metaData);
				if (logger.isDebugEnabled()) {
					logger.debug("json payload =" + jsonPayload);
				}
			} catch (JSONException e) {
				e.printStackTrace();
				logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
				throw new CloudException(e);
			}

			JSONObject patchedResponse = method.patch(GoogleMethod.SERVER + "/" + vmId, jsonPayload);

			if (logger.isDebugEnabled()) {
				logger.debug("json reponse =" + patchedResponse.toString());
			}

			String vmName = null;
			String status = method.getOperationStatus(GoogleMethod.OPERATION, patchedResponse);
			if (status != null && status.equals("DONE")) {
				if (patchedResponse.has("targetLink")) {
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
