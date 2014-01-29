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
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.util.ExceptionUtils;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.GooglePredicates;
import org.dasein.cloud.google.util.model.GoogleDisks;
import org.dasein.cloud.google.util.model.GoogleInstances;
import org.dasein.cloud.google.util.model.GoogleMachineTypes;
import org.dasein.cloud.google.util.model.GoogleOperations;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.Second;
import org.dasein.util.uom.time.TimePeriod;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.api.services.compute.model.Metadata.Items;
import static org.dasein.cloud.google.util.model.GoogleInstances.*;

/**
 * Implements the instances services supported in the Google Compute Engine API.
 *
 * @author igoonich
 * @since 2013.01
 */
public class GoogleServerSupport extends AbstractVMSupport<Google> {

	private static final Logger logger = Google.getLogger(GoogleServerSupport.class);

	private static final Collection<Architecture> SUPPORTED_ARCHITECTURES = ImmutableSet.of(Architecture.I32, Architecture.I64);

	private static final String GOOGLE_SERVER_TERM = "instance";
	private static final int DEFAULT_BOOT_VOLUME_SIZE = 10;

	private Google provider;
	private ExecutorService executor;

	public GoogleServerSupport(Google provider) {
		super(provider);
		this.provider = provider;
		this.executor = Executors.newCachedThreadPool();
	}

	public GoogleServerSupport(Google provider, ExecutorService executor) {
		super(provider);
		this.provider = provider;
		this.executor = executor;
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
		throw new OperationNotSupportedException("Enabling analytics not supported yet by GCE");
	}

	@Override
	public void enableAnalytics(String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Enabling analytics not supported yet by GCE");
	}

	@Override
	public String getConsoleOutput(String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Retrieving console output is not supported by GCE");
	}

	@Override
	public int getCostFactor(VmState state) throws InternalException, CloudException {
		return 0;
	}

	@Override
	public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
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
		return GOOGLE_SERVER_TERM;
	}

	@Override
	@Nullable
	public VirtualMachine getVirtualMachine(String virtualMachineId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		ProviderContext context = provider.getContext();
		Instance googleInstance = findInstance(virtualMachineId, context.getAccountNumber(), context.getRegionId());

		// TODO: get virtual machine with firewalls must contain attached firewalls #withFirewalls
		InstanceToDaseinVMConverter vmConverter = new InstanceToDaseinVMConverter(provider.getContext())
				.withMachineImage(provider.getComputeServices().getVolumeSupport());

		return googleInstance != null ? vmConverter.apply(googleInstance) : null;
	}

	/**
	 * Google doesn't provide method to fetch instances by Region only by DataCenter, therefore attempt to find disk in each zone of current
	 * region. Can return {@code null}
	 *
	 * @param instanceId instance id
	 * @param projectId  google project id
	 * @param regionId   region id
	 * @return
	 * @throws CloudException in case of any errors
	 */
	@Nullable
	protected Instance findInstance(String instanceId, String projectId, String regionId) throws CloudException {
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
			Compute.Instances.Get getInstanceRequest = compute.instances().get(provider.getContext().getAccountNumber(), zoneId, instanceId);
			Instance googleInstance = getInstanceRequest.execute();
			if (googleInstance != null) {
				return googleInstance;
			}
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}
		return null;
	}

	@Nonnull
	public Iterable<String> getVirtualMachineNamesWithVolume(String volumeId) throws CloudException {
		Preconditions.checkNotNull(volumeId);

		Iterable<VirtualMachine> virtualMachines = getVirtualMachinesWithVolume(volumeId);

		// Currently google doesn't support filters by embedded objects like disks,
		// therefore fetch all elements and loop
		List<String> vmNames = new ArrayList<String>();
		for (VirtualMachine virtualMachine : virtualMachines) {
			for (Volume volume : virtualMachine.getVolumes()) {
				if (volumeId.equals(volume.getName())) {
					vmNames.add(virtualMachine.getName());
				}
			}
		}

		return vmNames;
	}

	@Nullable
	protected Iterable<VirtualMachine> getVirtualMachinesWithVolume(String volumeId) throws CloudException {
		Preconditions.checkNotNull(volumeId);

		ProviderContext context = provider.getContext();
		Iterable<Instance> allInstances = listAllInstances(true);

		// Currently google doesn't support filters by embedded objects like disks,
		// therefore fetch all elements and find matching instances in the loop
		List<VirtualMachine> result = new ArrayList<VirtualMachine>();
		for (Instance googleInstance : allInstances) {
			for (AttachedDisk disk : googleInstance.getDisks()) {
				if (volumeId.equals(GoogleEndpoint.VOLUME.getResourceFromUrl(disk.getSource()))) {
					result.add(GoogleInstances.toDaseinVirtualMachine(googleInstance, context));
				}
			}
		}

		return result;
	}

	/**
	 * List all instance for current provider context. Caches results for a couple of seconds
	 *
	 * @return list of google instances
	 */
	private Iterable<Instance> listAllInstances(boolean useCache) throws CloudException {
		ProviderContext context = provider.getContext();

		if (!useCache) {
			return listInstances(VMFilterOptions.getInstance().matchingAny(), IdentityFunction.getInstance());
		}

		// TODO: align with Cameron the caching periods
		// fetch result from cache as this collection is fetched for each disk several times even in the same thread,
		// therefore should be cached at least for a few seconds (or can be made thread local)
		String cacheKey = context.getAccountNumber() + "-" + context.getRegionId() + "-google-instances";
		Cache<Instance> cache = Cache.getInstance(provider, cacheKey, Instance.class, CacheLevel.CLOUD_ACCOUNT,
				new TimePeriod<Second>(5, TimePeriod.SECOND));
		Collection<Instance> cachedInstances = (Collection<Instance>) cache.get(context);

		Iterable<Instance> instances;
		if (cachedInstances != null) {
			instances = cachedInstances;
		} else {
			instances = listInstances(VMFilterOptions.getInstance().matchingAny(), IdentityFunction.getInstance());
			cache.put(context, Lists.newArrayList(instances));
		}
		return instances;
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
		return cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE;
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
		return false;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p/> Note: currently there is no option to pass image type during the creation of instance, therefore root volume is created first and
	 * then is used as boot disk for google instance
	 */
	@Override
	@Nonnull
	public VirtualMachine launch(final VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {

		GoogleDiskSupport googleDiskSupport = provider.getComputeServices().getVolumeSupport();
		ProviderContext providerContext = provider.getContext();

		// new disks which can be removed if launch vm operation fails
		List<AttachedDisk> newDisks = new ArrayList<AttachedDisk>();

		// already existing disks which shouldn't be deleted
		List<AttachedDisk> existingDisks = new ArrayList<AttachedDisk>();

		// try to create attached disks sequentially
		try {
			for (final VMLaunchOptions.VolumeAttachment attachment : withLaunchOptions.getVolumes()) {
				VolumeCreateOptions volumeToCreate = attachment.volumeToCreate;
				if (attachment.existingVolumeId != null) {
					// add existing attached volume which is expected to be in the same zone as instance
					String volumeUrl = GoogleEndpoint.VOLUME.getEndpointUrl(attachment.existingVolumeId, providerContext.getAccountNumber(),
							withLaunchOptions.getDataCenterId());
					existingDisks.add(GoogleDisks.toAttachedDisk(new Disk().setSelfLink(volumeUrl)));
				} else if (volumeToCreate != null) {
					// additional volumes must be created in the same zone as instance
					volumeToCreate.inDataCenter(withLaunchOptions.getDataCenterId());

					AttachedDisk diskToAttach;
					if (!attachment.rootVolume) {
						// create and add new additional volume
						Disk googleDisk = googleDiskSupport.createDisk(GoogleDisks.from(volumeToCreate, providerContext));
						diskToAttach = GoogleDisks.toAttachedDisk(googleDisk)
								.setDeviceName(volumeToCreate.getDeviceId());
					} else {
						// create new boot volume
						Disk bootDisk = googleDiskSupport
								.createDisk(GoogleDisks.fromImage(withLaunchOptions.getMachineImageId(), volumeToCreate));
						diskToAttach = GoogleDisks.toAttachedDisk(bootDisk)
								.setBoot(true);
					}
					newDisks.add(diskToAttach);
				} else {
					throw new CloudException(String.format("Options are not completely provided for attachment: " +
							"[deviceId=%s, existingVolumeId=%s, rootVolume=%s, volumeToCreate=%s] ",
							attachment.deviceId, attachment.existingVolumeId, attachment.rootVolume,
							ToStringBuilder.reflectionToString(attachment.volumeToCreate, ToStringStyle.SHORT_PREFIX_STYLE)));
				}
			}
		} catch (CloudException e) {
			executor.submit(new DeleteAttachedDisks(newDisks, googleDiskSupport));
			throw e;
		} catch (Exception e) {
			executor.submit(new DeleteAttachedDisks(newDisks, googleDiskSupport));
			throw new CloudException(e);
		}

		List<AttachedDisk> attachedDisks = new ArrayList<AttachedDisk>(newDisks);
		attachedDisks.addAll(newDisks);
		attachedDisks.addAll(existingDisks);

		try {
			return launch(withLaunchOptions, attachedDisks);
		} catch (CloudException e) {
			executor.submit(new DeleteAttachedDisks(newDisks, googleDiskSupport));
			throw e;
		}
	}

	/**
	 * Command which deletes a bunch of attached disks
	 */
	private static class DeleteAttachedDisks implements Runnable {
		private Collection<AttachedDisk> disksToDelete;
		private GoogleDiskSupport googleDiskSupport;

		private DeleteAttachedDisks(Collection<AttachedDisk> disksToDelete, GoogleDiskSupport googleDiskSupport) {
			this.disksToDelete = disksToDelete;
			this.googleDiskSupport = googleDiskSupport;
		}

		@Override
		public void run() {
			for (AttachedDisk attachedDisk : disksToDelete) {
				String volumeId = GoogleEndpoint.VOLUME.getResourceFromUrl(attachedDisk.getSource());
				try {
					if (googleDiskSupport.getVolume(volumeId) != null) {
						googleDiskSupport.remove(volumeId);
					}
				} catch (Exception e) {
					logger.debug("Failed to delete volume '" + volumeId + "'", e);
				}
			}
		}
	}

	@Nonnull
	protected VirtualMachine launch(VMLaunchOptions withLaunchOptions, List<AttachedDisk> attachedDisks) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		final Instance googleInstance = GoogleInstances.from(withLaunchOptions, context)
				.setDisks(attachedDisks);

		Operation operation = null;
		try {
			logger.debug("Start launching virtual machine '{}'", withLaunchOptions.getHostName());
			Compute.Instances.Insert insertInstanceRequest = compute.instances()
					.insert(context.getAccountNumber(), googleInstance.getZone(), googleInstance);
			operation = insertInstanceRequest.execute();
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		OperationSupport operationSupport = provider.getComputeServices().getOperationsSupport();
		operationSupport.waitUntilOperationCompletes(operation, 180);

		// at this point it is expected that status is "DONE" for create operation
		return getVirtualMachine(googleInstance.getName());
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
		 * TODO: probably make sense just to take machine types form first available zone - it will speed up this action
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
	 * Generic method which produces a list of objects using a converting function from google instances <p/> Note: It is expected the every
	 * google zone name has region ID as prefix
	 *
	 * Currently GCE doesn't provide any option to search
	 *
	 * @param options           instances search options
	 * @param instanceConverter google instance converting function
	 * @param <T>               producing result type of {@code instanceConverter}
	 * @return list of instances
	 * @throws CloudException in case any error occurred within the cloud provider
	 */
	protected <T> Iterable<T> listInstances(VMFilterOptions options, Function<Instance, T> instanceConverter) throws CloudException {
		Preconditions.checkNotNull(options);
		Preconditions.checkNotNull(instanceConverter);

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		// currently GCE doesn't support filtering by tags as a part of the request "filter" parameter (or VMFilterOptions object)
		// therefore filtering is done using the following predicate
		Predicate<Instance> tagsFilter = GooglePredicates.createMetadataFilter(options.getTags());
		Predicate<Instance> labelsFilter = GooglePredicates.createGoogleTagsFilter(options.getLabels());

		return listInstances(options, instanceConverter, Predicates.<Instance>and(tagsFilter, labelsFilter));
	}

	/**
	 * Generic method which produces a list of objects using a converting function from google instances <p/> Note: It is expected the every
	 * google zone name has region ID as prefix
	 *
	 * @param options           instances search options
	 * @param instanceConverter google instance converting function
	 * @param instancesFilter   google instance filtering predicate
	 * @param <T>               producing result type of {@code instanceConverter}
	 * @return list of instances
	 * @throws CloudException in case any error occurred within the cloud provider
	 */
	protected <T> Iterable<T> listInstances(VMFilterOptions options, Function<Instance, T> instanceConverter,
											Predicate<Instance> instancesFilter) throws CloudException {
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

			// use provided 'regex' for filtering on GCE side
			listInstancesRequest.setFilter(options.getRegex());

			InstanceAggregatedList aggregatedList = listInstancesRequest.execute();
			Map<String, InstancesScopedList> instancesScopedListMap = aggregatedList.getItems();
			if (instancesScopedListMap != null && instancesScopedListMap.isEmpty()) {
				return Collections.emptyList();
			}

			List<Instance> googleInstances = new ArrayList<Instance>();
			for (String zone : instancesScopedListMap.keySet()) {
				if (zone.contains(context.getRegionId())) {
					InstancesScopedList instancesScopedList = instancesScopedListMap.get(zone);
					if (instancesScopedList.getInstances() != null) {
						for (Instance instance : instancesScopedList.getInstances()) {
							googleInstances.add(instance);
						}
					}
				}
			}

			return Iterables.transform(Iterables.filter(googleInstances, instancesFilter), instanceConverter);

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
		// find an instance in order to know as zoneId (is a mandatory field for delete operation)
		final VirtualMachine virtualMachine = getVirtualMachine(vmId);

		// trigger termination for instance
		Operation operation = terminateInBackground(vmId, virtualMachine.getProviderDataCenterId());

		// wait until instance is completely deleted (otherwise root volume cannot be removed)
		OperationSupport<Operation> operationSupport = provider.getComputeServices().getOperationsSupport();
		operationSupport.waitUntilOperationCompletes(operation, 180);

		// remove root volume
		GoogleDiskSupport googleDiskSupport = provider.getComputeServices().getVolumeSupport();
		Volume rootVolume = GoogleInstances.getRootVolume(virtualMachine);
		if (rootVolume == null) {
			throw new CloudException("Root volume wasn't found for virtual machine [" + virtualMachine.getName() + "]");
		}

		googleDiskSupport.remove(rootVolume.getProviderVolumeId(), virtualMachine.getProviderDataCenterId());
	}

	/**
	 * Method terminates and instance without boot volume. It doesn't wait until virtual machine termination process is completely finished on
	 * GCE side
	 *
	 * @param vmId   virtual machine ID
	 * @param zoneId google zone ID
	 */
	protected Operation terminateInBackground(@Nonnull String vmId, @Nonnull String zoneId) throws CloudException {
		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		try {
			Compute.Instances.Delete deleteInstanceRequest = compute.instances().delete(context.getAccountNumber(),
					zoneId, vmId);
			Operation operation = deleteInstanceRequest.execute();
			GoogleOperations.logOperationStatusOrFail(operation);
			return operation;
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to remove instance [" + vmId + "]");
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
		Preconditions.checkNotNull(tags);
		Preconditions.checkNotNull(vmId);

		Instance instance = findInstance(vmId, provider.getContext().getAccountNumber(), provider.getContext().getRegionId());
		if (instance == null) {
			throw new CloudException("Failed to update tags, as virtual machine with ID [" + vmId + "] doesn't exist");
		}

		updateTags(instance, tags);
	}

	protected void updateTags(Instance instance, Tag... tags) throws CloudException, InternalException {
		Metadata currentMetadata = instance.getMetadata();
		List<Items> itemsList = currentMetadata.getItems() != null ? currentMetadata.getItems() : new ArrayList<Items>();
		for (Tag tag : tags) {
			itemsList.add(new Items().setKey(tag.getKey()).setValue(tag.getValue()));
		}
		currentMetadata.setItems(itemsList);
		setGoogleMetadata(instance, currentMetadata);
	}

	/**
	 * Updates metadata object for google instance
	 *
	 * @param instance
	 * @param metadata
	 * @throws CloudException
	 * @throws InternalException
	 */
	protected void setGoogleMetadata(Instance instance, Metadata metadata) throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		String zoneId = GoogleEndpoint.ZONE.getResourceFromUrl(instance.getZone());

		Operation operation = null;
		try {
			logger.debug("Start updating tags [{}] for virtual machine [{}]", metadata.getItems(), instance.getName());
			Compute.Instances.SetMetadata setMetadataRequest = compute.instances()
					.setMetadata(provider.getContext().getAccountNumber(), zoneId, instance.getName(), metadata);
			operation = setMetadataRequest.execute();
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		OperationSupport<Operation> operationSupport = provider.getComputeServices().getOperationsSupport();
		operationSupport.waitUntilOperationCompletes(operation, 60);
	}

	@Override
	public void removeTags(String[] vmIds, Tag... tags) throws CloudException, InternalException {
		for (String vmId : vmIds) {
			removeTags(vmId, tags);
		}
	}

	@Override
	public void removeTags(String vmId, Tag... tags) throws CloudException, InternalException {
		Preconditions.checkNotNull(tags);
		Preconditions.checkNotNull(vmId);

		// fetch instance to get metadata fingerprint
		Instance instance = findInstance(vmId, provider.getContext().getAccountNumber(), provider.getContext().getRegionId());
		removeTags(instance, tags);
	}

	protected void removeTags(Instance instance, Tag... tags) throws CloudException, InternalException {
		Metadata currentMetadata = instance.getMetadata();

		if (currentMetadata.getItems() == null) {
			throw new CloudException("Instance [" + instance.getName() + "] doesn't have any tags");
		}

		Iterator<Items> iterator = currentMetadata.getItems().iterator();
		while (iterator.hasNext()) {
			Items items = iterator.next();
			for (Tag tag : tags) {
				// if value is empty and key matches then remove the tag
				if (tag.getKey().equals(items.getKey())
						&& (StringUtils.isEmpty(tag.getValue()) || tag.getValue().equals(items.getValue()))) {
					iterator.remove();
				}
			}
		}

		setGoogleMetadata(instance, currentMetadata);
	}

	/**
	 * Modifies virtual machine firewalls
	 *
	 * @param vmId      virtual machine ID
	 * @param firewalls updated firewalls IDs
	 * @return current virtual machine
	 * @throws CloudException an error occurred in the cloud processing the request
	 */
	@Override
	public VirtualMachine modifyInstance(@Nonnull String vmId, @Nonnull String[] firewalls) throws CloudException {
		Instance googleInstance = findInstance(vmId, provider.getContext().getAccountNumber(), provider.getContext().getRegionId());
		if (googleInstance == null) {
			throw new IllegalArgumentException("Instance with ID [" + vmId + "] doesn't exist");
		}

		updateGoogleTags(googleInstance, firewalls);

		return GoogleInstances.toDaseinVirtualMachine(googleInstance, provider.getContext());
	}

	/**
	 * Adds google tags to instance
	 *
	 * Note: Since Dasein tags are reserved by to google metadata, this method for adding google tags
	 *
	 * @param googleInstance google instance
	 * @param googleTags     vararg array of google tags
	 */
	protected void addGoogleTags(Instance googleInstance, String... googleTags) throws CloudException {
		Preconditions.checkNotNull(googleInstance);
		Preconditions.checkNotNull(googleTags);

		Tags tags = googleInstance.getTags() != null ? googleInstance.getTags() : new Tags();
		List<String> tagItems = tags.getItems() != null ? tags.getItems() : new ArrayList<String>();

		tagItems.addAll(Arrays.asList(googleTags));
		tags.setItems(tagItems);

		setGoogleTags(googleInstance, tags);
	}

	/**
	 * Adds google tags to instance
	 *
	 * Note: Since Dasein tags are reserved by to google metadata, this method for adding google tags
	 *
	 * @param googleInstance google instance
	 * @param updatedTags    vararg array of google tags
	 */
	protected void updateGoogleTags(Instance googleInstance, String... updatedTags) throws CloudException {
		Preconditions.checkNotNull(googleInstance);
		Preconditions.checkNotNull(updatedTags);

		Tags tags = googleInstance.getTags() != null ? googleInstance.getTags() : new Tags();
		tags.setItems(Arrays.asList(updatedTags));

		setGoogleTags(googleInstance, tags);
	}

	/**
	 * Removes google tags to instance
	 *
	 * Note: Since Dasein tags are reserved by to google metadata, this method for removing google tags
	 *
	 * @param googleInstance google instance
	 * @param googleTags     vararg array of google tags
	 */
	protected void removeGoogleTags(Instance googleInstance, String... googleTags) throws CloudException {
		Preconditions.checkNotNull(googleInstance);
		Preconditions.checkNotNull(googleTags);

		if (googleInstance.getTags() == null || googleInstance.getTags().getItems() == null) {
			throw new IllegalStateException("Google instance [" + googleInstance.getName() + "] doesn't have any tags");
		}

		Tags tags = googleInstance.getTags();
		Iterator<String> iterator = tags.getItems().iterator();
		while (iterator.hasNext()) {
			String nextTag = iterator.next();
			for (String tagToRemove : googleTags) {
				if (nextTag.equals(tagToRemove)) {
					iterator.remove();
				}
			}
		}

		setGoogleTags(googleInstance, tags);
	}

	protected void setGoogleTags(Instance googleInstance, Tags tags) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		String zoneId = GoogleEndpoint.ZONE.getResourceFromUrl(googleInstance.getZone());

		Operation operation = null;
		try {
			logger.debug("Start updating tags [{}] for virtual machine [{}]", tags.getItems(), googleInstance.getName());
			Compute.Instances.SetTags setTagsRequest = compute.instances()
					.setTags(provider.getContext().getAccountNumber(), zoneId, googleInstance.getName(), tags);
			operation = setTagsRequest.execute();
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		OperationSupport<Operation> operationSupport = provider.getComputeServices().getOperationsSupport();
		operationSupport.waitUntilOperationCompletes(operation, 60);

		googleInstance.setTags(tags);
	}

}
