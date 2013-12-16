package org.dasein.cloud.google.util.model;

import com.google.api.client.util.DateTime;
import com.google.api.client.util.Preconditions;
import com.google.api.services.compute.model.Disk;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.compute.VolumeState;
import org.dasein.cloud.compute.VolumeType;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.GoogleServerSupport;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;

/**
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleDisks {

	/**
	 * Data center extension to be used by default
	 */
	private static final String DEFAULT_DISK_ZONE_TYPE = "a";

	/**
	 * Create {@link Disk} object based on provided dasein {@link VolumeCreateOptions}
	 *
	 * @param createOptions dasein volume create options
	 * @param context       provider context
	 * @return google disk object to be created
	 */
	public static Disk from(VolumeCreateOptions createOptions, ProviderContext context) {
		Preconditions.checkNotNull(createOptions);
		Preconditions.checkNotNull(context);

		Disk googleDisk = new Disk();

		googleDisk.setName(createOptions.getName());
		googleDisk.setDescription(createOptions.getDescription());

		if (createOptions.getSnapshotId() != null) {
			googleDisk.setSourceSnapshot(GoogleEndpoint.SNAPSHOT.getEndpointUrl(context.getAccountNumber())
					+ createOptions.getSnapshotId());
		} else {
			googleDisk.setSizeGb(createOptions.getVolumeSize().getQuantity().longValue());
		}

		if (createOptions.getDataCenterId() != null) {
			googleDisk.setZone(createOptions.getDataCenterId());
		} else {
			googleDisk.setZone(context.getRegionId() + "-" + DEFAULT_DISK_ZONE_TYPE);
		}

		return googleDisk;
	}

	public static Volume toDaseinVolume(Disk googleVolume, Google provider) throws CloudException {
		Preconditions.checkNotNull(googleVolume);
		Preconditions.checkNotNull(provider);

		Volume volume = new Volume();

		// default properties
		volume.setType(VolumeType.HDD);
		volume.setProviderRegionId(provider.getContext().getRegionId());

		volume.setProviderVolumeId(googleVolume.getName());
		volume.setName(googleVolume.getName());
		volume.setDescription(googleVolume.getDescription());
		volume.setSize(new Storage<Gigabyte>(googleVolume.getSizeGb(), Storage.GIGABYTE));

		volume.setProviderSnapshotId(googleVolume.getSourceSnapshot() != null
				? GoogleEndpoint.SNAPSHOT.getResourceFromUrl(googleVolume.getSourceSnapshot()) : null);

		volume.setProviderDataCenterId(googleVolume.getZone() != null
				? GoogleEndpoint.ZONE.getResourceFromUrl(googleVolume.getZone()) : null);
		volume.setCreationTimestamp(DateTime.parseRfc3339(googleVolume.getCreationTimestamp()).getValue());

		if ("CREATING".equals(googleVolume.getStatus())) {
			volume.setCurrentState(VolumeState.PENDING);
		} else if ("READY".equals(googleVolume.getStatus())) {
			volume.setCurrentState(VolumeState.AVAILABLE);
		} else {
			volume.setCurrentState(VolumeState.DELETED);
		}

		try {
			GoogleServerSupport virtualMachineSupport = provider.getComputeServices().getVirtualMachineSupport();
			Iterable<String> vmIds = virtualMachineSupport.getVirtualMachinesWithVolume(volume.getProviderVolumeId());
			if (vmIds != null && vmIds.iterator().hasNext()) {
				volume.setProviderVirtualMachineId(vmIds.iterator().next());
			}
		} catch (InternalException e) {
			throw new CloudException(e);
		}

		return volume;
	}

}
