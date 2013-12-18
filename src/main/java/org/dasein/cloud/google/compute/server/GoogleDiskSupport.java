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
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.DiskList;
import com.google.api.services.compute.model.Operation;
import com.google.common.base.Function;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.NoContextException;
import org.dasein.cloud.google.util.ExceptionUtils;
import org.dasein.cloud.google.util.model.GoogleDisks;
import org.dasein.cloud.google.util.model.GoogleOperations;
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
import java.util.concurrent.*;

import static org.dasein.cloud.google.util.ExceptionUtils.handleGoogleResponseError;
import static org.dasein.cloud.google.util.model.GoogleOperations.OperationResource;

/**
 * Implements the volume services supported in the Google API.
 *
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleDiskSupport implements VolumeSupport {

	private static final Logger logger = Google.getLogger(GoogleDiskSupport.class);

	private Google provider;

	public GoogleDiskSupport(Google provider) {
		this.provider = provider;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public void attach(String volumeId, String toServer, String deviceId) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support attaching volumes to an instance");
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

	@Nonnull
	@Override
	public Iterable<VmState> getAttachStates(@Nullable Volume volume) {
		logger.warn("Operation not supported yet");
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public Iterable<VmState> getDetachStates(@Nullable Volume volume) {
		logger.warn("Operation not supported yet");
		return Collections.emptyList();
	}

	@Override
	@Nonnull
	public String createVolume(VolumeCreateOptions options) throws InternalException, CloudException {
		Disk googleDisk = GoogleDisks.from(options, provider.getContext());
		return createVolume(googleDisk);
	}

	@Nonnull
	public String createVolumeFromImage(String sourceImageId, VolumeCreateOptions options) throws InternalException, CloudException {
		Disk googleDisk = GoogleDisks.fromImage(sourceImageId, options);
		return createVolume(googleDisk);
	}

	@Nonnull
	protected String createVolume(Disk googleDisk) throws InternalException, CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		Operation operation = null;
		try {
			Compute.Disks.Insert insertDiskRequest = compute.disks().insert(provider.getContext().getAccountNumber(),
					googleDisk.getZone(), googleDisk);
			operation = insertDiskRequest.execute();
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		GoogleOperations.logOperationStatusOrFail(operation, OperationResource.DISK);

		return StringUtils.substringAfterLast(operation.getTargetLink(), "/");
	}

	/**
	 * Periodically tries to retrieve volume or fails after some timeout. As a result of this method completely initialized volume is retrieved
	 * with status {@link VolumeState#AVAILABLE}
	 *
	 * @param volumeId                       volume ID
	 * @param periodInSecondsBetweenAttempts period in seconds between fetch attempts
	 * @param timeoutInSeconds               maximum delay in seconds when to stop trying
	 * @return dasein virtual machine
	 * @throws CloudException in case of any errors
	 */
	protected Volume getVolumeUtilAvailable(final String volumeId, final long periodInSecondsBetweenAttempts,
											final long timeoutInSeconds) throws CloudException {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Volume> futureVolume = executor.submit(new Callable<Volume>() {
			@Override
			public Volume call() throws Exception {
				Volume volume = null;
				// wail till volume is completely available
				while (volume == null || VolumeState.PENDING.equals(volume.getCurrentState())) {
					logger.debug("Volume '{}' is not created yet, next attempt in {} sec", volumeId, periodInSecondsBetweenAttempts);
					TimeUnit.SECONDS.sleep(periodInSecondsBetweenAttempts);
					volume = getVolume(volumeId);
				}
				return volume;
			}
		});

		executor.shutdown();

		try {
			return futureVolume.get(timeoutInSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new CloudException(e);
		} catch (ExecutionException e) {
			throw new CloudException(e.getCause());
		} catch (TimeoutException e) {
			throw new CloudException("Couldn't retrieve instance [" + volumeId + "] in " + timeoutInSeconds + " seconds");
		}
	}

	@Override
	public void detach(String volumeId) throws InternalException,
			CloudException {
		throw new OperationNotSupportedException("Google does not support detach volumes from a running instance");
	}

	@Override
	public void detach(String volumeId, boolean force)
			throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support detach volumes from a running instance");
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
		// TODO: Need to check what is the minimum volume size supported by GCE
		return new Storage<Gigabyte>(10, Storage.GIGABYTE);
	}

	@Override
	public String getProviderTermForVolume(Locale locale) {
		return "disk";
	}

	@Override
	public Volume getVolume(String volumeId) throws InternalException, CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		ProviderContext context = provider.getContext();
		Disk googleDisk = findDisk(volumeId, context.getAccountNumber(), context.getRegionId());
		return googleDisk != null ? GoogleDisks.toDaseinVolume(googleDisk, context) : null;
	}

	/**
	 * Retrieves an image ID for current volume if exist, {@code null} if image doesn't exist for this volume. For some reason there is no
	 * image name related field in the dasein {@link Volume}.
	 *
	 * @param volumeId volume ID
	 * @return source image ID
	 * @throws CloudException an error occurred with the cloud provider while fetching the volume
	 */
	public String getVolumeImage(String volumeId, String zoneId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		ProviderContext context = provider.getContext();
		Disk googleDisk = findDiskInZone(volumeId, context.getAccountNumber(), zoneId);
		return googleDisk.getSourceImage();
	}

	/**
	 * Google doesn't provide method to fetch disks by Region only by DataCenter, therefore attempt to find disk in each zone of current
	 * region
	 */
	protected Disk findDisk(String volumeId, String projectId, String regionId) throws InternalException, CloudException {
		Iterable<DataCenter> dataCentersInRegion = provider.getDataCenterServices().listDataCenters(regionId);
		for (DataCenter dataCenter : dataCentersInRegion) {
			Disk disk = findDiskInZone(volumeId, projectId, dataCenter.getName());
			if (disk != null) {
				return disk;
			}
		}
		return null;
	}

	protected Disk findDiskInZone(String volumeId, String projectId, String zoneId) throws CloudException {
		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Disks.Get getDiskRequest = compute.disks().get(projectId, zoneId, volumeId);
			Disk googleDisk = getDiskRequest.execute();
			if (googleDisk != null) {
				return googleDisk;
			}
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		return null;
	}

	@Override
	public Requirement getVolumeProductRequirement() throws InternalException,
			CloudException {
		return Requirement.NONE;
	}

	@Override
	public boolean isVolumeSizeDeterminedByProduct() throws InternalException,
			CloudException {
		return true;
	}

	@Override
	public Iterable<String> listPossibleDeviceIds(Platform platform)
			throws InternalException, CloudException {
		ArrayList<String> list = new ArrayList<String>();

		if (!platform.isWindows()) {
			list.add("/dev/sdf");
			list.add("/dev/sdg");
			list.add("/dev/sdh");
			list.add("/dev/sdi");
			list.add("/dev/sdj");
			list.add("/dev/sdk");
			list.add("/dev/sdl");
			list.add("/dev/sdm");
			list.add("/dev/sdn");
			list.add("/dev/sdo");
			list.add("/dev/sdp");
			list.add("/dev/sdq");
			list.add("/dev/sdr");
			list.add("/dev/sds");
			list.add("/dev/sdt");
		}
		return list;

	}

	@Override
	public Iterable<VolumeFormat> listSupportedFormats()
			throws InternalException, CloudException {
		return Collections.singletonList(VolumeFormat.BLOCK);
	}

	@Override
	public Iterable<VolumeProduct> listVolumeProducts()
			throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<ResourceStatus> listVolumeStatus()
			throws InternalException, CloudException {
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
		Function<Disk, Volume> disksConverter = new GoogleDisks.ToDasinVolume(provider.getContext())
				.withAttachedVirtualMachines(provider.getComputeServices().getVirtualMachineSupport());
		return listVolumes(options, disksConverter);
	}

	public <T> Iterable<T> listVolumes(VolumeFilterOptions options, Function<Disk, T> disksConverter) throws InternalException, CloudException {
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
						volumes.add(disksConverter.apply(googleDisk));
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
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		// find a disk, as zoneId is a mandatory for delete operation
		Volume volume = getVolume(volumeId);

		Operation operation = null;
		try {
			Compute.Disks.Delete deleteDiskRequest = compute.disks().delete(context.getAccountNumber(),
					volume.getProviderDataCenterId(), volumeId);
			operation = deleteDiskRequest.execute();
		} catch (IOException e) {
			ExceptionUtils.handleGoogleResponseError(e);
		}

		GoogleOperations.logOperationStatusOrFail(operation, OperationResource.DISK);
	}

	@Override
	public void removeTags(String volumeId, Tag... tags) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Google volume does not contain meta data");
	}

	@Override
	public void removeTags(String[] volumeIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain meta data");
	}

	@Override
	public void updateTags(String volumeId, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain meta data");
	}

	@Override
	public void updateTags(String[] volumeIds, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain meta data");
	}

}
