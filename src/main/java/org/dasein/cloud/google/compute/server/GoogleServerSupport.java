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
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.NoContextException;
import org.dasein.cloud.google.util.ExceptionUtils;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.model.GoogleInstances;
import org.dasein.cloud.google.util.model.GoogleMachineTypes;
import org.dasein.cloud.google.util.model.GoogleOperations;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.Second;
import org.dasein.util.uom.time.TimePeriod;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static com.google.api.services.compute.model.Metadata.Items;
import static org.dasein.cloud.google.util.model.GoogleInstances.*;
import static org.dasein.cloud.google.util.model.GoogleOperations.OperationResource;
import static org.dasein.cloud.google.util.model.GoogleOperations.OperationStatus;

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
		return null;
	}

	@Override
	public VirtualMachine clone(String vmId, String intoDcId, String name, String description, boolean powerOn, String... firewallIds)
			throws InternalException, CloudException {
		return null;
	}

	@Override
	public VMScalingCapabilities describeVerticalScalingCapabilities() throws CloudException, InternalException {
		return null;
	}

	@Override
	public void disableAnalytics(String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Enabling analytics not supported yet");
	}

	@Override
	public void enableAnalytics(String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Enabling analytics not supported yet");
	}

	@Override
	public String getConsoleOutput(String vmId) throws InternalException, CloudException {
		return null;
	}

	@Override
	public int getCostFactor(VmState state) throws InternalException, CloudException {
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
	public Iterable<String> getVirtualMachinesWithVolume(String volumeId) throws CloudException {
		Preconditions.checkNotNull(volumeId);

		ProviderContext context = provider.getContext();

		// Load from cache if possible, as this collection is fetched for each disk several times even in the same thread
		// therefore should be cached at least for a few seconds (or can be made ThreadLocal)
		String cacheKey = context.getAccountNumber() + "-" + context.getRegionId() + "-google-instances";
		Cache<Instance> cache = Cache.getInstance(provider, cacheKey, Instance.class, CacheLevel.CLOUD_ACCOUNT,
				new TimePeriod<Second>(15, TimePeriod.SECOND));
		Collection<Instance> cachedInstances = (Collection<Instance>) cache.get(context);

		// Currently google doesn't support filters by embedded objects like disks,
		// therefore fetch all elements if not cached and loop
		Iterable<Instance> instances = cachedInstances != null ? cachedInstances
				: listInstances(VMFilterOptions.getInstance(), IdentityFunction.getInstance());

		// put instances in cache
		cache.put(context, instances);

		List<String> vmNames = new ArrayList<String>();
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
	public Requirement identifyPasswordRequirement(Platform platform) throws CloudException, InternalException {
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
	public Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public Requirement identifyVlanRequirement() throws CloudException, InternalException {
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
		// currently there is no option to pass image type during the creation of instance
		// therefore we will create a root volume first and then use it as boot disk for google instance
		final GoogleDiskSupport googleDiskSupport = provider.getComputeServices().getVolumeSupport();

		VolumeCreateOptions volumeCreateOptions = VolumeCreateOptions.getInstance(new Storage<Gigabyte>(10, Storage.GIGABYTE),
				withLaunchOptions.getHostName(), withLaunchOptions.getDescription()).inDataCenter(withLaunchOptions.getDataCenterId());
		final String volumeId = googleDiskSupport.createVolumeFromImage(withLaunchOptions.getMachineImageId(), volumeCreateOptions);
		// wait till boot volume is initialized
		Volume volume = googleDiskSupport.getVolumeUtilAvailable(volumeId, 3, 60);
		try {
			return launch(withLaunchOptions, volume.getName());
		} catch (CloudException e) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.submit(new Runnable() {
				@Override
				public void run() {
					try {
						googleDiskSupport.remove(volumeId);
					} catch (Exception e) {
						logger.error("Failed to delete volume: " + volumeId);
					}
				}
			});
			executor.shutdown();
			// rethrow after scheduling the delete
			throw e;
		}
	}

	@Nonnull
	protected VirtualMachine launch(VMLaunchOptions withLaunchOptions, String bootDiskName) throws CloudException, InternalException {

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		final Instance googleInstance = GoogleInstances.from(withLaunchOptions, context, bootDiskName);

		Operation operation = null;
		try {
			logger.debug("Start launching virtual machine '{}' with boot volume '{}'", withLaunchOptions.getHostName(), bootDiskName);
			Compute.Instances.Insert insertInstanceRequest = compute.instances()
					.insert(context.getAccountNumber(), googleInstance.getZone(), googleInstance);
			operation = insertInstanceRequest.execute();
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		// log create operation status or throw and exception in case of failure
		GoogleOperations.logOperationStatusOrFail(operation, OperationResource.INSTANCE);

		OperationStatus status = OperationStatus.fromString(operation.getStatus());
		switch (status) {
			case DONE:
				// at this point it is expected that if status is "DONE" then instance must be created
				return getVirtualMachine(googleInstance.getName());
			default:
				// 5 seconds between attempts plus wait at most 1.5 minutes
				return getVirtualMachineUtilReachable(googleInstance.getName(), 5, 90);
		}
	}

	/**
	 * Periodically tries to retrieve virtual machine until succeeds or fails after some timeout. This method DOESN'T require returned instance
	 * to be in some specific state of {@link VmState}
	 *
	 * @param vmId                           instance ID
	 * @param periodInSecondsBetweenAttempts period in seconds between fetch attempts
	 * @param timeoutInSeconds               maximum delay in seconds when to stop trying
	 * @return dasein virtual machine
	 * @throws CloudException in case of any errors
	 */
	protected VirtualMachine getVirtualMachineUtilReachable(final String vmId, final long periodInSecondsBetweenAttempts,
															final long timeoutInSeconds) throws CloudException {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<VirtualMachine> futureVm = executor.submit(new Callable<VirtualMachine>() {
			@Override
			public VirtualMachine call() throws Exception {
				VirtualMachine virtualMachine = null;
				while (virtualMachine == null) {
					logger.debug("Virtual machine '{}' is not created yet, next attempt in {} sec", vmId, periodInSecondsBetweenAttempts);
					TimeUnit.SECONDS.sleep(periodInSecondsBetweenAttempts);
					virtualMachine = getVirtualMachine(vmId);
				}
				return virtualMachine;
			}
		});

		executor.shutdown();

		try {
			return futureVm.get(timeoutInSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new CloudException(e);
		} catch (ExecutionException e) {
			throw new CloudException(e.getCause());
		} catch (TimeoutException e) {
			// stop trying
			futureVm.cancel(true);
			throw new CloudException("Couldn't retrieve instance [" + vmId + "] in " + timeoutInSeconds + " seconds");
		}
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
			return listInstances(VMFilterOptions.getInstance(), InstanceToDaseinStatusConverter.getInstance());
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
			return listInstances(options, new InstanceToDaseinVMConverter(provider.getContext())
					.withMachineImage(provider.getComputeServices().getVolumeSupport()));
		} finally {
			APITrace.end();
		}
	}

	/**
	 * Generic method which produces a list of objects using a converting function from google instances
	 *
	 * Note: It is expected the every google zone name has region ID as prefix
	 *
	 * @param options           instances search options
	 * @param instanceConverter google instance converting function
	 * @param <T>               producing result type of {@code instanceConverter}
	 * @return
	 * @throws InternalException represents a local failure
	 * @throws CloudException    any other errors
	 */
	protected <T> Iterable<T> listInstances(VMFilterOptions options, Function<Instance, T> instanceConverter) throws CloudException {
		Preconditions.checkNotNull(options);
		Preconditions.checkNotNull(instanceConverter);

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		try {
			Compute.Instances.AggregatedList listInstancesRequest
					= compute.instances().aggregatedList(provider.getContext().getAccountNumber());
			InstanceAggregatedList aggregatedList = listInstancesRequest.execute();
			Map<String, InstancesScopedList> instancesScopedListMap = aggregatedList.getItems();
			if (instancesScopedListMap != null && instancesScopedListMap.isEmpty()) {
				return Collections.emptyList();
			}

			List<T> dasinObjects = new ArrayList<T>();
			for (String zone : instancesScopedListMap.keySet()) {
				if (zone.contains(context.getRegionId())) {
					InstancesScopedList instancesScopedList = instancesScopedListMap.get(zone);
					if (instancesScopedList.getInstances() != null) {
						for (Instance instance : instancesScopedList.getInstances()) {
							dasinObjects.add(instanceConverter.apply(instance));
						}
					}
				}
			}

			return dasinObjects;
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		return Collections.emptyList();
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
		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		// find an instance, as zoneId is a mandatory for delete operation
		VirtualMachine volume = getVirtualMachine(vmId);

		Operation operation = null;
		try {
			Compute.Instances.Delete deleteInstanceRequest = compute.instances().delete(context.getAccountNumber(),
					volume.getProviderDataCenterId(), vmId);
			operation = deleteInstanceRequest.execute();
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		// TODO: wait until unreachable and then delete boot disk

		GoogleOperations.logOperationStatusOrFail(operation, OperationResource.INSTANCE);
	}

	@Override
	public void unpause(String vmId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support unpausing vms");

	}

	@Override
	public void updateTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		for (String vmId : vmIds) {
			updateTags(vmId, tags);
		}
	}

	@Override
	public void updateTags(String vmId, Tag... tags) throws CloudException, InternalException {
		VirtualMachine virtualMachine = getVirtualMachine(vmId);
		if (virtualMachine == null) {
			throw new CloudException("Failed to update tags, as virtual machine with ID [" + vmId + "] doesn't exist");
		}
		updateTags(virtualMachine.getName(), virtualMachine.getProviderDataCenterId(), tags);
	}

	protected void updateTags(String vmId, String zoneId, Tag... tags) throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		List<Items> itemsList = new ArrayList<Items>();
		for (Tag tag : tags) {
			itemsList.add(new Items().setKey(tag.getKey()).setValue(tag.getValue()));
		}
		Metadata metadata = new Metadata().setItems(itemsList);

		Operation operation = null;
		try {
			logger.trace("Start setting tags [{}] for virtual machine '{}'", tags, vmId);
			Compute.Instances.SetMetadata setMetadataRequest = compute.instances()
					.setMetadata(provider.getContext().getAccountNumber(), zoneId, vmId, metadata);
			operation = setMetadataRequest.execute();
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		GoogleOperations.logOperationStatusOrFail(operation, OperationResource.INSTANCE);
	}

	@Override
	public void removeTags(String vmId, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support removing meta data from vms");
	}

	@Override
	public void removeTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support removing meta data from vms");
	}

}
