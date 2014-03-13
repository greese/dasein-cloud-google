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
import org.dasein.cloud.google.compute.GoogleCompute;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.GoogleLogger;
import org.dasein.cloud.google.util.filter.InstancePredicates;
import org.dasein.cloud.google.util.model.GoogleDisks;
import org.dasein.cloud.google.util.model.GoogleInstances;
import org.dasein.cloud.google.util.model.GoogleMachineTypes;
import org.dasein.cloud.google.util.model.GoogleOperations;
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
import java.util.concurrent.*;

import static com.google.api.services.compute.model.Metadata.Items;
import static org.dasein.cloud.google.util.model.GoogleDisks.AttachedDiskType;
import static org.dasein.cloud.google.util.model.GoogleDisks.RichAttachedDisk;
import static org.dasein.cloud.google.util.model.GoogleInstances.*;

/**
 * Implements the instances services supported in the Google Compute Engine API.
 *
 * @author igoonich
 * @since 2013.01
 */
public class GoogleServerSupport extends AbstractVMSupport<Google> {

	private static final Logger logger = GoogleLogger.getLogger(GoogleServerSupport.class);

	private static final Collection<Architecture> SUPPORTED_ARCHITECTURES = ImmutableSet.of(Architecture.I32, Architecture.I64);

	private static final String GOOGLE_SERVER_TERM = "instance";

	private ExecutorService executor;
	private OperationSupport<Operation> operationSupport;

	private GoogleDiskSupport googleDiskSupport;
	private CreateAttachedDisksStrategy createAttachedDisksStrategy;
	private GoogleAttachmentsFactory googleAttachmentsFactory;

	public GoogleServerSupport(Google provider) {
		this(provider, Executors.newCachedThreadPool());
	}

	public GoogleServerSupport(Google provider, ExecutorService executor) {
		super(provider);
		initInjectedServices(executor);
	}

	private void initInjectedServices(ExecutorService executor) {
		this.executor = executor;

		GoogleCompute googleCompute = getProvider().getComputeServices();
		this.operationSupport = googleCompute.getOperationsSupport();
		this.googleDiskSupport = googleCompute.getVolumeSupport();

		this.googleAttachmentsFactory = new GoogleAttachmentsFactory(getProvider().getContext(), googleDiskSupport);

		// by default create attached disks sequentially
		this.createAttachedDisksStrategy = new CreateAttachedDisksConcurrently(executor, googleDiskSupport, googleAttachmentsFactory);
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
	public String getConsoleOutput(String vmId) throws CloudException, InternalException {
		if (!getProvider().isInitialized()) {
			throw new NoContextException();
		}

		ProviderContext context = getProvider().getContext();

		// fetch instance in order to find out the exact zone
		Instance instance = findInstance(vmId, context.getAccountNumber(), context.getRegionId());
		String zoneId = GoogleEndpoint.ZONE.getResourceFromUrl(instance.getZone());

		Compute compute = getProvider().getGoogleCompute();
		try {
			Compute.Instances.GetSerialPortOutput getSerialPortOutputRequest
					= compute.instances().getSerialPortOutput(context.getAccountNumber(), zoneId, vmId);
			SerialPortOutput serialPortOutput = getSerialPortOutputRequest.execute();
			return serialPortOutput.getContents();
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to retrieve console output");
	}

	@Override
	public String getProviderTermForServer(Locale locale) {
		return GOOGLE_SERVER_TERM;
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
	public @Nullable VirtualMachine getVirtualMachine(String virtualMachineId) throws CloudException {
		if (!getProvider().isInitialized()) {
			throw new NoContextException();
		}
		ProviderContext context = getProvider().getContext();
		Instance googleInstance = findInstance(virtualMachineId, context.getAccountNumber(), context.getRegionId());

		InstanceToDaseinVMConverter vmConverter = new InstanceToDaseinVMConverter(getProvider().getContext())
				.withMachineImage(getProvider().getComputeServices().getVolumeSupport());

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
	protected @Nullable Instance findInstance(String instanceId, String projectId, String regionId) throws CloudException {
		Iterable<DataCenter> dataCentersInRegion = getProvider().getDataCenterServices().listDataCenters(regionId);
		for (DataCenter dataCenter : dataCentersInRegion) {
			Instance instance = findInstanceInZone(instanceId, projectId, dataCenter.getName());
			if (instance != null) {
				return instance;
			}
		}
		return null;
	}

	protected @Nullable Instance findInstanceInZone(String instanceId, String projectId, String zoneId) throws CloudException {
		Compute compute = getProvider().getGoogleCompute();
		try {
			Compute.Instances.Get getInstanceRequest = compute.instances().get(getProvider().getContext().getAccountNumber(), zoneId, instanceId);
			Instance googleInstance = getInstanceRequest.execute();
			if (googleInstance != null) {
				return googleInstance;
			}
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}
		return null;
	}

	public @Nonnull Iterable<String> getVirtualMachineNamesWithVolume(String volumeId) throws CloudException {
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

	protected @Nullable Iterable<VirtualMachine> getVirtualMachinesWithVolume(String volumeId) throws CloudException {
		Preconditions.checkNotNull(volumeId);

		ProviderContext context = getProvider().getContext();
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
		ProviderContext context = getProvider().getContext();

		if (!useCache) {
			return listInstances(VMFilterOptions.getInstance().matchingAny(), IdentityFunction.getInstance());
		}

		// TODO: align with Cameron the caching periods
		// fetch result from cache as this collection is fetched for each disk several times even in the same thread,
		// therefore should be cached at least for a few seconds (or can be made thread local)
		String cacheKey = context.getAccountNumber() + "-" + context.getRegionId() + "-google-instances";
		Cache<Instance> cache = Cache.getInstance(getProvider(), cacheKey, Instance.class, CacheLevel.CLOUD_ACCOUNT,
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
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p> Note: currently there is no option to pass image type during the creation of instance, therefore root volume is created first and
	 * then is used as boot disk for google instance
	 */
	@Override
	public @Nonnull VirtualMachine launch(final VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {
		final GoogleDiskSupport googleDiskSupport = getProvider().getComputeServices().getVolumeSupport();

		// try to create attached disks
		Collection<RichAttachedDisk> attachedDisks = createAttachedDisksStrategy.createAttachedDisks(withLaunchOptions);

		try {
			return launch(withLaunchOptions, attachedDisks);
		} catch (CloudException e) {
			executor.submit(new DeleteAttachedDisks(attachedDisks, googleDiskSupport));
			throw e;
		}
	}

	private abstract static class AbstractDeleteAttachedDisks implements Runnable {
		protected GoogleDiskSupport googleDiskSupport;

		protected AbstractDeleteAttachedDisks(GoogleDiskSupport googleDiskSupport) {
			this.googleDiskSupport = googleDiskSupport;
		}

		protected void deleteAttachedDisk(RichAttachedDisk richAttachedDisk) {
			// prevent deleting existing disks
			if (!AttachedDiskType.EXISTING.equals(richAttachedDisk.getAttachedDiskType())) {
				AttachedDisk attachedDisk = richAttachedDisk.getAttachedDisk();
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

	/**
	 * Command which deletes a bunch of attached disks
	 */
	private static class DeleteAttachedDisks extends AbstractDeleteAttachedDisks {
		protected Collection<RichAttachedDisk> disksToDelete;

		private DeleteAttachedDisks(Collection<RichAttachedDisk> disksToDelete, GoogleDiskSupport googleDiskSupport) {
			super(googleDiskSupport);
			this.disksToDelete = disksToDelete;
		}

		@Override
		public void run() {
			for (RichAttachedDisk richAttachedDisk : disksToDelete) {
				deleteAttachedDisk(richAttachedDisk);
			}
		}
	}

	/**
	 * Factory for creating volume attachments based on dasein attachment object
	 */
	public static class GoogleAttachmentsFactory {

		private GoogleDiskSupport googleDiskSupport;
		private ProviderContext providerContext;

		public GoogleAttachmentsFactory(ProviderContext providerContext, GoogleDiskSupport googleDiskSupport) {
			this.providerContext = providerContext;
			this.googleDiskSupport = googleDiskSupport;
		}

		/**
		 * Create {@link RichAttachedDisk} from dasein volume attachment properties
		 *
		 * @param attachment dasein volume attachment
		 * @param options    additional options
		 * @return
		 * @throws CloudException
		 */
		public RichAttachedDisk createAttachedDisk(VolumeAttachment attachment, VMLaunchOptions options) throws CloudException {
			VolumeCreateOptions volumeToCreate = attachment.volumeToCreate;

			if (attachment.existingVolumeId != null) {
				AttachedDisk existingAttachedDisk = getExistingVolume(attachment.existingVolumeId, options.getDataCenterId());
				// check weather existing volume should be used as boot volume
				if (attachment.isRootVolume()) {
					existingAttachedDisk.setBoot(true);
					return new RichAttachedDisk(AttachedDiskType.BOOT, existingAttachedDisk);
				}
				return new RichAttachedDisk(AttachedDiskType.EXISTING, existingAttachedDisk);
			}

			if (volumeToCreate != null) {
				// additional volumes must be created in the same zone
				volumeToCreate.inDataCenter(options.getDataCenterId());
				if (attachment.rootVolume) {
					AttachedDisk bootAttachedDisk = createBootVolume(attachment.volumeToCreate, options.getMachineImageId());
					return new RichAttachedDisk(AttachedDiskType.BOOT, bootAttachedDisk);
				} else {
					// TODO: remove the VolumeAttachment#volumeToCreate property in 'dasin-cloud-core' as it is duplicated with VolumeCreateOptions#deviceId
					AttachedDisk standardAttachedDisk = createStandardVolume(attachment.volumeToCreate, attachment.deviceId);
					return new RichAttachedDisk(AttachedDiskType.STANDARD, standardAttachedDisk);
				}
			}

			throw new CloudException(String.format("Cannot figure out volume attachment type: [deviceId=%s, existingVolumeId=%s, " +
					"rootVolume=%s, volumeToCreate=%s] ", attachment.deviceId, attachment.existingVolumeId, attachment.rootVolume,
					ToStringBuilder.reflectionToString(attachment.volumeToCreate, ToStringStyle.SHORT_PREFIX_STYLE)));

		}

		protected AttachedDisk createBootVolume(VolumeCreateOptions volumeToCreate, String imageId) throws CloudException {
			Disk bootDisk = googleDiskSupport
					.createDisk(GoogleDisks.fromImage(imageId, volumeToCreate));
			return GoogleDisks.toAttachedDisk(bootDisk).setBoot(true);
		}

		protected AttachedDisk createStandardVolume(VolumeCreateOptions volumeToCreate, String deviceId) throws CloudException {
			Disk googleDisk = googleDiskSupport.createDisk(GoogleDisks.from(volumeToCreate, providerContext));
			return GoogleDisks.toAttachedDisk(googleDisk).setDeviceName(deviceId);
		}

		protected AttachedDisk getExistingVolume(String existingVolumeId, String dataCenterId) {
			// add existing attached volume which is expected to be in the same zone as instance
			String volumeUrl = GoogleEndpoint.VOLUME.getEndpointUrl(existingVolumeId, providerContext.getAccountNumber(), dataCenterId);
			return GoogleDisks.toAttachedDisk(new Disk().setSelfLink(volumeUrl));
		}

	}

	/**
	 * Strategy for creating attached disks
	 */
	public interface CreateAttachedDisksStrategy {

		Collection<RichAttachedDisk> createAttachedDisks(VMLaunchOptions withLaunchOptions) throws CloudException;

	}

	/**
	 * Abstract disk creation strategy
	 */
	private static abstract class AbstractCreateAttachedDisksStrategy implements CreateAttachedDisksStrategy {

		protected ExecutorService executor;
		protected GoogleDiskSupport googleDiskSupport;
		protected GoogleAttachmentsFactory googleAttachmentsFactory;

		private AbstractCreateAttachedDisksStrategy(ExecutorService executor, GoogleDiskSupport googleDiskSupport,
													GoogleAttachmentsFactory googleAttachmentsFactory) {
			this.executor = executor;
			this.googleDiskSupport = googleDiskSupport;
			this.googleAttachmentsFactory = googleAttachmentsFactory;
		}

	}

	/**
	 * Strategy for sequential attachments creation
	 *
	 * <p> Creates attached disks for instance one by one
	 */
	private static class CreateAttachedDisksSequentially extends AbstractCreateAttachedDisksStrategy {

		private CreateAttachedDisksSequentially(ExecutorService executor, GoogleDiskSupport googleDiskSupport,
												GoogleAttachmentsFactory googleAttachmentsFactory) {
			super(executor, googleDiskSupport, googleAttachmentsFactory);
		}

		@Override
		public Collection<RichAttachedDisk> createAttachedDisks(final VMLaunchOptions withLaunchOptions) throws CloudException {
			List<RichAttachedDisk> attachedDisks = new ArrayList<RichAttachedDisk>();

			try {
				for (VolumeAttachment attachment : withLaunchOptions.getVolumes()) {
					attachedDisks.add(googleAttachmentsFactory.createAttachedDisk(attachment, withLaunchOptions));
				}
			} catch (CloudException e) {
				executor.submit(new DeleteAttachedDisks(attachedDisks, googleDiskSupport));
				throw e;
			} catch (Exception e) {
				executor.submit(new DeleteAttachedDisks(attachedDisks, googleDiskSupport));
				throw new CloudException(e);
			}

			return attachedDisks;
		}

	}

	/**
	 * Strategy for sequential attachments creation
	 *
	 * <p> Creates attached disks for instance in parallel
	 */
	private static class CreateAttachedDisksConcurrently extends AbstractCreateAttachedDisksStrategy {

		/**
		 * Wait timeout in seconds for {@link RichAttachedDisk} creation/retrieve action, be default is set to 5 minutes
		 */
		private static final int WAIT_TIMEOUT = 300;

		private CreateAttachedDisksConcurrently(ExecutorService executor, GoogleDiskSupport googleDiskSupport,
												GoogleAttachmentsFactory googleAttachmentsFactory) {
			super(executor, googleDiskSupport, googleAttachmentsFactory);
		}

		@Override
		public Collection<RichAttachedDisk> createAttachedDisks(final VMLaunchOptions withLaunchOptions) throws CloudException {
			List<Future<RichAttachedDisk>> attachedDiskFutures = new ArrayList<Future<RichAttachedDisk>>();

			for (final VolumeAttachment attachment : withLaunchOptions.getVolumes()) {
				attachedDiskFutures.add(executor.submit(new Callable<RichAttachedDisk>() {
					@Override
					public RichAttachedDisk call() throws CloudException {
						return googleAttachmentsFactory.createAttachedDisk(attachment, withLaunchOptions);
					}
				}));
			}

			List<RichAttachedDisk> richAttachedDisks = new ArrayList<RichAttachedDisk>();
			try {
				for (Future<RichAttachedDisk> attachedDiskFuture : attachedDiskFutures) {
					richAttachedDisks.add(attachedDiskFuture.get(WAIT_TIMEOUT, TimeUnit.SECONDS));
				}
			} catch (InterruptedException e) {
				executor.submit(new DeleteFutureAttachedDisks(googleDiskSupport, attachedDiskFutures));
				throw new CloudException("Failed to create attached disk", e);
			} catch (ExecutionException e) {
				executor.submit(new DeleteFutureAttachedDisks(googleDiskSupport, attachedDiskFutures));
				throw new CloudException("Failed to create attached disk", e.getCause());
			} catch (TimeoutException e) {
				executor.submit(new DeleteFutureAttachedDisks(googleDiskSupport, attachedDiskFutures));
				throw new CloudException("Failed to create attached disk in " + WAIT_TIMEOUT + " seconds", e);
			}

			return richAttachedDisks;
		}

		/**
		 * Waits till all creation operations completely finish and then remove each created attached disk one by one
		 */
		private static class DeleteFutureAttachedDisks extends AbstractDeleteAttachedDisks {

			protected Collection<Future<RichAttachedDisk>> disksToDeleteFutures;

			private DeleteFutureAttachedDisks(GoogleDiskSupport googleDiskSupport, Collection<Future<RichAttachedDisk>> disksToDeleteFutures) {
				super(googleDiskSupport);
				this.disksToDeleteFutures = disksToDeleteFutures;
			}

			@Override
			public void run() {
				for (Future<RichAttachedDisk> diskToDeleteFuture : disksToDeleteFutures) {
					RichAttachedDisk richAttachedDisk = getAttachedDisk(diskToDeleteFuture);
					if (richAttachedDisk != null) {
						deleteAttachedDisk(richAttachedDisk);
					}
				}
			}

			/**
			 * Waits till future create operation completes
			 *
			 * Can be {@code null} if not found or failed
			 *
			 * @param richAttachedDiskFuture future attached disks
			 * @return created attached disk
			 */
			protected RichAttachedDisk getAttachedDisk(Future<RichAttachedDisk> richAttachedDiskFuture) {
				try {
					return richAttachedDiskFuture.get(WAIT_TIMEOUT, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					logger.error("Failed to finish attached disk operation before removing: {}", e.getMessage());
				} catch (ExecutionException e) {
					// expected behaviour for already failed attached disk
					logger.debug("Failed to finish attached disk operation before removing, will be skipped: {}", e.getCause().getMessage());
				} catch (TimeoutException e) {
					richAttachedDiskFuture.cancel(true);
					logger.error("Failed to finish attached disk operation before removing in {} seconds: {}", WAIT_TIMEOUT, e.getMessage());
				}
				return null;
			}
		}

	}

	protected @Nonnull VirtualMachine launch(VMLaunchOptions withLaunchOptions, Collection<RichAttachedDisk> attachedDisks)
			throws CloudException {

		Preconditions.checkNotNull(withLaunchOptions);
		Preconditions.checkNotNull(attachedDisks);

		long start = System.currentTimeMillis();
		try {
			if (!getProvider().isInitialized()) {
				throw new NoContextException();
			}

			Compute compute = getProvider().getGoogleCompute();
			ProviderContext context = getProvider().getContext();

			Instance googleInstance = GoogleInstances.from(withLaunchOptions, attachedDisks, context);

			Operation operation = null;
			try {
				logger.debug("Start launching virtual machine '{}'", withLaunchOptions.getHostName());
				Compute.Instances.Insert insertInstanceRequest = compute.instances()
						.insert(context.getAccountNumber(), googleInstance.getZone(), googleInstance);
				operation = insertInstanceRequest.execute();
			} catch (IOException e) {
				GoogleExceptionUtils.handleGoogleResponseError(e);
			}

			operationSupport.waitUntilOperationCompletes(operation);

			// at this point it is expected that create operation completed
			return getVirtualMachine(googleInstance.getName());
		} finally {
			logger.debug("Instance [{}] launching took {} ms", withLaunchOptions.getHostName(), System.currentTimeMillis() - start);
		}
	}

	@Override
	public Iterable<String> listFirewalls(String vmId) throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<VirtualMachineProduct> listProducts(Architecture architecture) throws InternalException, CloudException {
		if (!getProvider().isInitialized()) {
			throw new NoContextException();
		}

		ProviderContext context = getProvider().getContext();

		// load from cache if possible
		String cacheKey = getMachineTypesRegionKey(context.getAccountNumber(), context.getRegionId(), architecture);
		Cache<VirtualMachineProduct> cache = Cache.getInstance(getProvider(), cacheKey, VirtualMachineProduct.class, CacheLevel.CLOUD_ACCOUNT,
				new TimePeriod<Hour>(1, TimePeriod.HOUR));
		Collection<VirtualMachineProduct> cachedProducts = (Collection<VirtualMachineProduct>) cache.get(context);
		if (cachedProducts != null) {
			return cachedProducts;
		}

		Compute compute = getProvider().getGoogleCompute();

		/*
		 * For some reason google requires to specify zone for machine types, but in the same time
		 * they look completely the same in each zone and not even distinguished in GCE console
		 * TODO: clarify how to handle machine types per zone, for now return a unique set of machine types per region
		 * TODO: probably make sense just to take machine types form first available zone - it will speed up this action
		 */
		Map<String, VirtualMachineProduct> products = Maps.newHashMap();
		Iterable<DataCenter> dataCenters = getProvider().getDataCenterServices().listDataCenters(context.getRegionId());
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
				GoogleExceptionUtils.handleGoogleResponseError(e);
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
		APITrace.begin(getProvider(), "listVirtualMachineStatus");
		try {
			return listInstances(VMFilterOptions.getInstance(), InstanceToDaseinResourceStatusConverter.getInstance());
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
		APITrace.begin(getProvider(), "listVirtualMachines");
		try {
			return listInstances(options, new InstanceToDaseinVMConverter(getProvider().getContext())
					.withMachineImage(getProvider().getComputeServices().getVolumeSupport()));
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

		if (!getProvider().isInitialized()) {
			throw new NoContextException();
		}

		// currently GCE doesn't support filtering by tags as a part of the request "filter" parameter (or VMFilterOptions object)
		// therefore filtering is done using the following predicate

		return listInstances(options, instanceConverter, InstancePredicates.getOptionsFilter(options));
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

		if (!getProvider().isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = getProvider().getGoogleCompute();
		ProviderContext context = getProvider().getContext();

		try {
			Compute.Instances.AggregatedList listInstancesRequest
					= compute.instances().aggregatedList(getProvider().getContext().getAccountNumber());

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
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		return Collections.emptyList();
	}

	@Override
	public void pause(String vmId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support pausing vms");
	}

	/**
	 * Attempts to reboot instance
	 *
	 * Note: operation is triggered in background
	 *
	 * @param vmId virtual machine ID
	 * @throws CloudException in case of any dasin errors
	 */
	@Override
	public void reboot(String vmId) throws CloudException {
		ProviderContext context = getProvider().getContext();
		Compute compute = getProvider().getGoogleCompute();

		Instance instance = findInstance(vmId, context.getAccountNumber(), context.getRegionId());
		String zoneId = GoogleEndpoint.ZONE.getResourceFromUrl(instance.getZone());

		try {
			Compute.Instances.Reset resetInstanceRequest
					= compute.instances().reset(context.getAccountNumber(), zoneId, instance.getName());
			resetInstanceRequest.execute();
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}
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
		// find an instance in order to know the exact zone ID (which is a mandatory field for delete operation)
		final VirtualMachine virtualMachine = getVirtualMachine(vmId);

		if (virtualMachine == null) {
			throw new IllegalArgumentException("Virtual machine with ID [" + vmId + "] doesn't exist");
		}

		Operation operation = terminateInBackground(vmId, virtualMachine.getProviderDataCenterId());

		// wait until instance is completely deleted (otherwise root volume cannot be removed)
		operationSupport.waitUntilOperationCompletes(operation);

		// remove root volume
		GoogleDiskSupport googleDiskSupport = getProvider().getComputeServices().getVolumeSupport();
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
		Compute compute = getProvider().getGoogleCompute();
		ProviderContext context = getProvider().getContext();

		try {
			Compute.Instances.Delete deleteInstanceRequest = compute.instances().delete(context.getAccountNumber(),
					zoneId, vmId);
			Operation operation = deleteInstanceRequest.execute();
			GoogleOperations.logOperationStatusOrFail(operation);
			return operation;
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
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
	public void updateTags(String vmId, Tag... tags) throws CloudException {
		Preconditions.checkNotNull(tags);
		Preconditions.checkNotNull(vmId);

		Instance instance = findInstance(vmId, getProvider().getContext().getAccountNumber(), getProvider().getContext().getRegionId());
		if (instance == null) {
			throw new CloudException("Failed to update tags, as virtual machine with ID [" + vmId + "] doesn't exist");
		}

		updateTags(instance, tags);
	}

	protected void updateTags(Instance instance, Tag... tags) throws CloudException {
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
	protected void setGoogleMetadata(Instance instance, Metadata metadata) throws CloudException {
		if (!getProvider().isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = getProvider().getGoogleCompute();
		String zoneId = GoogleEndpoint.ZONE.getResourceFromUrl(instance.getZone());

		Operation operation = null;
		try {
			logger.debug("Start updating tags [{}] for virtual machine [{}]", metadata.getItems(), instance.getName());
			Compute.Instances.SetMetadata setMetadataRequest = compute.instances()
					.setMetadata(getProvider().getContext().getAccountNumber(), zoneId, instance.getName(), metadata);
			operation = setMetadataRequest.execute();
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		operationSupport.waitUntilOperationCompletes(operation);
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
		Instance instance = findInstance(vmId, getProvider().getContext().getAccountNumber(), getProvider().getContext().getRegionId());
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
		Instance googleInstance = findInstance(vmId, getProvider().getContext().getAccountNumber(), getProvider().getContext().getRegionId());
		if (googleInstance == null) {
			throw new IllegalArgumentException("Instance with ID [" + vmId + "] doesn't exist");
		}

		updateGoogleTags(googleInstance, firewalls);

		return GoogleInstances.toDaseinVirtualMachine(googleInstance, getProvider().getContext());
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
		if (!getProvider().isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = getProvider().getGoogleCompute();
		String zoneId = GoogleEndpoint.ZONE.getResourceFromUrl(googleInstance.getZone());

		Operation operation = null;
		try {
			logger.debug("Start updating tags [{}] for virtual machine [{}]", tags.getItems(), googleInstance.getName());
			Compute.Instances.SetTags setTagsRequest = compute.instances()
					.setTags(getProvider().getContext().getAccountNumber(), zoneId, googleInstance.getName(), tags);
			operation = setTagsRequest.execute();
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		operationSupport.waitUntilOperationCompletes(operation);

		googleInstance.setTags(tags);
	}

}
