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

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.DiskList;
import com.google.api.services.compute.model.Operation;
import com.google.common.base.Function;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.GoogleLogger;
import org.dasein.cloud.google.util.model.GoogleDisks;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.dasein.cloud.google.util.GoogleExceptionUtils.handleGoogleResponseError;

/**
 * Implements the volume services supported in the Google API.
 *
 * @since 2013.01
 */
public class GoogleDiskSupport implements VolumeSupport {

	private static final Logger logger = GoogleLogger.getLogger(GoogleDiskSupport.class);

	private static final String GOOGLE_VOLUME_TERM = "disk";

	private Google provider;
	private OperationSupport<Operation> operationSupport;

	public GoogleDiskSupport(Google provider) {
		this.provider = provider;
		this.operationSupport = provider.getComputeServices().getOperationsSupport();
	}

	@Override
	public String create(String fromSnapshot, int sizeInGb, String inZone) throws InternalException, CloudException {
		if (fromSnapshot != null) {
			return createVolume(VolumeCreateOptions.getInstanceForSnapshot(fromSnapshot, new Storage<Gigabyte>(sizeInGb, Storage.GIGABYTE),
					"dsn-auto-volume", "dsn-auto-volume").inDataCenter(inZone));
		} else {
			return createVolume(VolumeCreateOptions.getInstance(new Storage<Gigabyte>(sizeInGb, Storage.GIGABYTE),
					"dsn-auto-volume", "dsn-auto-volume").inDataCenter(inZone));
		}
	}

	@Override
	public @Nonnull Iterable<VmState> getAttachStates(@Nullable Volume volume) {
		logger.warn("Operation not supported yet");
		return Collections.emptyList();
	}

	@Override
	public @Nonnull Iterable<VmState> getDetachStates(@Nullable Volume volume) {
		logger.warn("Operation not supported yet");
		return Collections.emptyList();
	}

	/**
	 * Google disk is created synchronously
	 *
	 * {@inheritDoc}
	 */
	@Override
	public @Nonnull String createVolume(VolumeCreateOptions options) throws InternalException, CloudException {
		Disk googleDisk = GoogleDisks.from(options, provider.getContext());
		Operation operation = submitDiskCreationOperation(googleDisk);
		operationSupport.waitUntilOperationCompletes(operation);
		return googleDisk.getName();
	}

	/**
	 * Creates a volume. Waits until operation completely finishes.
	 *
	 * @param options volume create options
	 * @return created volume object
	 * @throws CloudException
	 */
	public Volume createVolumeSynchronously(VolumeCreateOptions options) throws CloudException {
		Disk googleDisk = GoogleDisks.from(options, provider.getContext());
		return GoogleDisks.toDaseinVolume(createDisk(googleDisk), provider.getContext());
	}

	/**
	 * Creates a volume. Waits until operation completely finishes. This method is added because {@link VolumeCreateOptions} doesn't include
	 * machine image property for some reason
	 *
	 * @param options volume create options
	 * @return created volume object
	 * @throws CloudException
	 */
	protected Volume createVolumeFromImage(String sourceImageId, VolumeCreateOptions options) throws InternalException, CloudException {
		Disk googleDisk = GoogleDisks.fromImage(sourceImageId, options);
		return GoogleDisks.toDaseinVolume(createDisk(googleDisk), provider.getContext());
	}

	/**
	 * Create google disk. Waits until operation completely finishes
	 *
	 * @param googleDisk google disk to be created
	 * @return disk name
	 * @throws CloudException in case of any errors
	 */
	protected @Nonnull Disk createDisk(Disk googleDisk) throws CloudException {
		long start = System.currentTimeMillis();
		try {
			Operation operation = submitDiskCreationOperation(googleDisk);

			operationSupport.waitUntilOperationCompletes(operation);

			return findDiskInZone(googleDisk.getName(), provider.getContext().getAccountNumber(), googleDisk.getZone());
		} finally {
			logger.debug("Disk [{}] creation took {} ms", googleDisk.getName(), System.currentTimeMillis() - start);
		}
	}

	/**
	 * Submits google disk creation command to GCE
	 *
	 * @param googleDisk google disk to be created
	 * @return create operation scheduled on google side
	 * @throws CloudException in case of any errors
	 */
	protected @Nonnull Operation submitDiskCreationOperation(Disk googleDisk) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Disks.Insert insertDiskRequest = compute.disks().insert(provider.getContext().getAccountNumber(),
					googleDisk.getZone(), googleDisk);
			// also set "sourceImage" as POST parameter of the request in case present
			// because google doesn't always handle for some reason corresponding JSON parameter
			if (googleDisk.getSourceImage() != null) {
				insertDiskRequest.setSourceImage(googleDisk.getSourceImage());
			}
			return insertDiskRequest.execute();
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to create disk [" + googleDisk.getName() + "]");
	}

	@Override
	public void attach(String volumeId, String toServer, String deviceId) throws InternalException, CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		Disk googleDisk = findDisk(volumeId, context.getAccountNumber(), context.getRegionId());
		String zoneId = GoogleEndpoint.ZONE.getResourceFromUrl(googleDisk.getZone());

		try {
			logger.debug("Attaching volume [{}] (device: '{}') to instance [{}]", volumeId, deviceId, toServer);
			AttachedDisk attachedDisk = GoogleDisks.toAttachedDisk(googleDisk).setDeviceName(deviceId);

			Compute.Instances.AttachDisk attachDiskRequest = compute.instances()
					.attachDisk(context.getAccountNumber(), zoneId, toServer, attachedDisk);
			Operation operation = attachDiskRequest.execute();

			operationSupport.waitUntilOperationCompletes(operation);
		} catch (IOException e) {
			// fail in case resource not found, means that smb tries to attach disk to server form wrong data center
			// or attaching instance doesn't exist
			GoogleExceptionUtils.handleGoogleResponseError(e, false);
		}
	}

	@Override
	public void detach(String volumeId) throws InternalException, CloudException {
		detach(volumeId, false);
	}

	/**
	 * Align what reading and writing disks modes are currently supported For now it is expected that all google disks are being created in
	 * {@link GoogleDisks.DiskMode#READ_WRITE} mode
	 */
	@Override
	public void detach(String volumeId, boolean force) throws CloudException {
		// fetch all virtual machines attached to disk
		GoogleServerSupport googleServerSupport = provider.getComputeServices().getVirtualMachineSupport();
		Iterable<VirtualMachine> virtualMachines = googleServerSupport.getVirtualMachinesWithVolume(volumeId);

		for (VirtualMachine virtualMachine : virtualMachines) {
			// find the device ID for the volume
			for (Volume volume : virtualMachine.getVolumes()) {
				if (volumeId.equalsIgnoreCase(volume.getName())) {
					// passing instance data center as volumes must be in the same zone
					detach(volumeId, virtualMachine.getName(), volume.getDeviceId(), virtualMachine.getProviderDataCenterId());
				}
			}
		}
	}

	/**
	 * Detaches volume mounted using some device name form specific instance
	 *
	 * @param volumeId   volume ID
	 * @param fromServer instance ID
	 * @param deviceId   unique device name /dev/ name of the linux OS
	 * @throws CloudException in case detach operation fails
	 */
	protected void detach(String volumeId, String fromServer, String deviceId, String dataCenter) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		try {
			logger.debug("Detaching volume [{}] (device: '{}') from instance [{}]", volumeId, deviceId, fromServer);
			Compute.Instances.DetachDisk detachDiskRequest = compute.instances()
					.detachDisk(context.getAccountNumber(), dataCenter, fromServer, deviceId);
			Operation operation = detachDiskRequest.execute();

			operationSupport.waitUntilOperationCompletes(operation);
		} catch (IOException e) {
			// fail in case resource not found
			GoogleExceptionUtils.handleGoogleResponseError(e, false);
		}
	}

	@Override
	public int getMaximumVolumeCount() throws InternalException, CloudException {
		return -2;
	}

	@Override
	public Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException {
		// Setting the size of a persistent disk
		return new Storage<Gigabyte>(1024, Storage.GIGABYTE);
	}

	@Override
	public Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException {
		// TODO: Need to check what is the minimum volume size supported by GCE (may vary depending on the disk type)
		return new Storage<Gigabyte>(10, Storage.GIGABYTE);
	}

	@Override
	public String getProviderTermForVolume(Locale locale) {
		return GOOGLE_VOLUME_TERM;
	}

	@Override
	public Volume getVolume(String volumeId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		ProviderContext context = provider.getContext();
		Disk googleDisk = findDisk(volumeId, context.getAccountNumber(), context.getRegionId());

		Function<Disk, Volume> diskConverter = new GoogleDisks.ToDasinVolumeConverter(provider.getContext())
				.withAttachedVirtualMachines(provider.getComputeServices().getVirtualMachineSupport());
		return googleDisk != null ? diskConverter.apply(googleDisk) : null;
	}

	/**
	 * Retrieves an image ID for current volume if exist, {@code null} if image doesn't exist for this volume
	 *
	 * @param volumeId volume ID
	 * @param zoneId   zone ID
	 * @return source image ID
	 * @throws CloudException an error occurred with the cloud provider while fetching the volume
	 */
	public @Nullable String getVolumeImage(String volumeId, String zoneId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		ProviderContext context = provider.getContext();
		Disk googleDisk = findDiskInZone(volumeId, context.getAccountNumber(), zoneId);
		return googleDisk.getSourceImage() != null ? GoogleEndpoint.IMAGE.getResourceFromUrl(googleDisk.getSourceImage()) : null;
	}

	/**
	 * Google doesn't provide method to fetch disks by Region only by DataCenter, therefore attempt to find disk in each zone of current
	 * region
	 *
	 * Be aware the device name is not included in the google disk object (only available in google instance)
	 *
	 * @param volumeId  volume ID
	 * @param projectId google project ID
	 * @param regionId  zone ID
	 */
	protected Disk findDisk(String volumeId, String projectId, String regionId) throws CloudException {
		Iterable<DataCenter> dataCentersInRegion = provider.getDataCenterServices().listDataCenters(regionId);
		for (DataCenter dataCenter : dataCentersInRegion) {
			Disk disk = findDiskInZone(volumeId, projectId, dataCenter.getName());
			if (disk != null) {
				return disk;
			}
		}
		return null;
	}

	protected @Nullable Disk findDiskInZone(String volumeId, String projectId, String zoneId) throws CloudException {
		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Disks.Get getDiskRequest = compute.disks().get(projectId, zoneId, volumeId);
			Disk googleDisk = getDiskRequest.execute();
			if (googleDisk != null) {
				return googleDisk;
			}
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		return null;
	}

	@Override
	public Iterable<ResourceStatus> listVolumeStatus() throws InternalException, CloudException {
		List<ResourceStatus> status = new ArrayList<ResourceStatus>();

		Iterable<Volume> volumes = listVolumes();
		for (Volume volume : volumes) {
			VolumeState state = volume.getCurrentState();
			ResourceStatus resStatus = new ResourceStatus(volume.getProviderVolumeId(), state);
			status.add(resStatus);
		}
		return status;
	}

	@Override
	public Iterable<Volume> listVolumes() throws InternalException, CloudException {
		return listVolumes(VolumeFilterOptions.getInstance());
	}

	@Override
	public Iterable<Volume> listVolumes(VolumeFilterOptions options) throws InternalException, CloudException {
		Function<Disk, Volume> diskConverter = new GoogleDisks.ToDasinVolumeConverter(provider.getContext())
				.withAttachedVirtualMachines(provider.getComputeServices().getVirtualMachineSupport());
		return listVolumes(options, diskConverter);
	}

	public <T> Iterable<T> listVolumes(VolumeFilterOptions options, Function<Disk, T> diskConverter) throws InternalException, CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		List<T> volumes = new ArrayList<T>();

		// Google doesn't provide method to fetch disks by Region only by DataCenter
		Iterable<DataCenter> dataCenters = provider.getDataCenterServices().listDataCenters(context.getRegionId());
		try {
			for (DataCenter dataCenter : dataCenters) {
				Compute.Disks.List listDisksRequest = compute.disks().list(provider.getContext().getAccountNumber(), dataCenter.getName());
				// TODO: looks like options are always empty - check!
				listDisksRequest.setFilter(options.getRegex());

				DiskList diskList = listDisksRequest.execute();
				if (diskList.getItems() != null) {
					for (Disk googleDisk : diskList.getItems()) {
						volumes.add(diskConverter.apply(googleDisk));
					}
				}
			}
		} catch (IOException e) {
			handleGoogleResponseError(e);
		}

		return volumes;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public void remove(String volumeId) throws InternalException, CloudException {
		// find a disk, as zoneId is a mandatory for delete operation
		Volume volume = getVolume(volumeId);
		remove(volumeId, volume.getProviderDataCenterId());
	}

	protected void remove(String volumeId, String zoneId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		Operation operation = null;
		try {
			Compute.Disks.Delete deleteDiskRequest = compute.disks().delete(context.getAccountNumber(), zoneId, volumeId);
			operation = deleteDiskRequest.execute();
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		operationSupport.waitUntilOperationCompletes(operation);
	}

	@Override
	public void removeTags(String volumeId, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain metadata");
	}

	@Override
	public void removeTags(String[] volumeIds, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain metadata");
	}

	@Override
	public void updateTags(String volumeId, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain metadata");
	}

	@Override
	public void updateTags(String[] volumeIds, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain metadata");
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public Requirement getVolumeProductRequirement() throws InternalException, CloudException {
		return Requirement.NONE;
	}

	@Override
	public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException {
		return false;
	}

	@Override
	public Iterable<String> listPossibleDeviceIds(Platform platform) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Listing possible devices IDs is not supported");
	}

	@Override
	public Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException {
		return Collections.singletonList(VolumeFormat.BLOCK);
	}

	@Override
	public Iterable<VolumeProduct> listVolumeProducts() throws InternalException, CloudException {
		return Collections.emptyList();
	}

}
