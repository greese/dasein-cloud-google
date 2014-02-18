package org.dasein.cloud.google.util.model;

import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Disk;
import com.google.common.base.Function;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.GoogleServerSupport;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class contains a static factory methods that allows volumes to be converted to Dasein objects (and vice-a-versa). This class also
 * contains various methods for manipulating google volumes which are not provided by default via GCE java API.
 *
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleDisks {

	private static final Logger logger = Google.getLogger(GoogleDisks.class);

	/**
	 * Data center extension to be used by default
	 */
	private static final String DEFAULT_DISK_ZONE_TYPE = "a";

	/**
	 * Persistent disk type. Currently the only type of disks which accepted by google
	 */
	public static final String PERSISTENT_DISK_TYPE = "PERSISTENT";

	/**
	 * Google disks read mode
	 */
	public enum DiskMode {

		/**
		 * Read-only mode. Persistent disk can be attached to multiple instances in this mode.
		 */
		READ_ONLY,

		/**
		 * read-write mode. Persistent disk can be attached to a single instance in read-write mode.
		 */
		READ_WRITE

	}

	/**
	 * Wrapper version of google attachment
	 */
	public static class RichAttachedDisk {
		private AttachedDisk attachedDisk;
		private AttachedDiskType attachedDiskType;

		public RichAttachedDisk(AttachedDiskType attachedDiskType, AttachedDisk attachedDisk) {
			this.attachedDiskType = checkNotNull(attachedDiskType);
			this.attachedDisk = checkNotNull(attachedDisk);
		}

		public AttachedDiskType getAttachedDiskType() {
			return attachedDiskType;
		}

		public AttachedDisk getAttachedDisk() {
			return attachedDisk;
		}
	}

	/**
	 * Dasein attached volume creation type
	 */
	public enum AttachedDiskType {

		/**
		 * Create boot volume operation
		 */
		BOOT,

		/**
		 * Do not create volume, use existing one
		 */
		EXISTING,

		/**
		 * Create standard volume
		 */
		STANDARD;

		/**
		 * Get volume creation type
		 *
		 * @param attachment dasein volume attachment
		 * @return volume creation type
		 */
		public static AttachedDiskType from(VMLaunchOptions.VolumeAttachment attachment) throws CloudException {
			VolumeCreateOptions volumeToCreate = attachment.volumeToCreate;
			if (attachment.existingVolumeId != null) {
				return EXISTING;
			} else if (volumeToCreate != null) {
				if (!attachment.rootVolume) {
					return BOOT;
				} else {
					return STANDARD;
				}
			}
			throw new CloudException(String.format("Cannot figure out volume attachment type: [deviceId=%s, existingVolumeId=%s, " +
					"rootVolume=%s, volumeToCreate=%s] ", attachment.deviceId, attachment.existingVolumeId, attachment.rootVolume,
					ToStringBuilder.reflectionToString(attachment.volumeToCreate, ToStringStyle.SHORT_PREFIX_STYLE)));
		}

	}

	/**
	 * Create {@link Disk} object based on provided dasein {@link VolumeCreateOptions}
	 *
	 * @param createOptions dasein volume create options
	 * @param context       provider context
	 * @return google disk object to be created
	 */
	public static Disk from(VolumeCreateOptions createOptions, ProviderContext context) {
		checkNotNull(createOptions);
		checkNotNull(context);
		checkNotNull(createOptions.getName(), "Name is missing for volume");

		Disk googleDisk = new Disk();

		googleDisk.setName(createOptions.getName());
		googleDisk.setDescription(createOptions.getDescription());

		if (createOptions.getSnapshotId() != null) {
			googleDisk.setSourceSnapshot(GoogleEndpoint.SNAPSHOT.getEndpointUrl(createOptions.getSnapshotId(), context.getAccountNumber()));
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

	/**
	 * Create {@link Disk} from image {@code sourceImageId} using provided create options {@link VolumeCreateOptions}
	 *
	 * @param createOptions dasein volume create options
	 * @return google disk object to be created
	 */
	public static Disk fromImage(String sourceImageId, VolumeCreateOptions createOptions) {
		checkNotNull(sourceImageId);
		checkNotNull(createOptions);

		Disk googleDisk = new Disk();
		googleDisk.setName(createOptions.getName());
		googleDisk.setDescription(createOptions.getDescription());
		// TODO: align which approach to choose for storing the source image ID
		googleDisk.setSourceImage(GoogleEndpoint.IMAGE.getEndpointUrl(sourceImageId));
		googleDisk.setZone(createOptions.getDataCenterId());
		googleDisk.setSizeGb(createOptions.getVolumeSize().getQuantity().longValue());
		return googleDisk;
	}

	public static AttachedDisk toAttachedDisk(Disk googleDisk) {
		checkNotNull(googleDisk);
		return new AttachedDisk()
				.setSource(googleDisk.getSelfLink())
				.setMode(DiskMode.READ_WRITE.toString())
				.setType(PERSISTENT_DISK_TYPE);
	}

	public static Volume toDaseinVolume(Disk googleDisk, ProviderContext context) {
		checkNotNull(googleDisk);
		checkNotNull(context);

		Volume volume = new Volume();

		// default properties
		volume.setType(VolumeType.HDD);
		volume.setProviderRegionId(context.getRegionId());

		volume.setProviderVolumeId(googleDisk.getName());
		volume.setName(googleDisk.getName());
		volume.setDescription(googleDisk.getDescription());
		volume.setSize(new Storage<Gigabyte>(googleDisk.getSizeGb(), Storage.GIGABYTE));

		volume.setProviderSnapshotId(googleDisk.getSourceSnapshot() != null
				? GoogleEndpoint.SNAPSHOT.getResourceFromUrl(googleDisk.getSourceSnapshot()) : null);

		volume.setProviderDataCenterId(googleDisk.getZone() != null
				? GoogleEndpoint.ZONE.getResourceFromUrl(googleDisk.getZone()) : null);
		volume.setCreationTimestamp(DateTime.parseRfc3339(googleDisk.getCreationTimestamp()).getValue());

		if ("CREATING".equals(googleDisk.getStatus())) {
			volume.setCurrentState(VolumeState.PENDING);
		} else if ("READY".equals(googleDisk.getStatus())) {
			volume.setCurrentState(VolumeState.AVAILABLE);
		} else {
			volume.setCurrentState(VolumeState.DELETED);
		}

		return volume;
	}

	/**
	 * Strategy for converting between google disks and dasein volumes
	 */
	public static final class ToDasinVolume implements Function<Disk, Volume> {
		private ProviderContext context;
		private GoogleServerSupport googleServerSupport;

		public ToDasinVolume(ProviderContext context) {
			this.context = context;
		}

		/**
		 * Configures and option to include a list of virtual machines connected to volume
		 *
		 * This method requires a google vms service in order to fetch all the instances connected to current volume
		 *
		 * @param googleServerSupport google instances support service
		 * @return same converter (builder variation)
		 */
		public ToDasinVolume withAttachedVirtualMachines(GoogleServerSupport googleServerSupport) {
			this.googleServerSupport = checkNotNull(googleServerSupport);
			return this;
		}

		@Nullable
		@Override
		public Volume apply(@Nullable Disk input) {
			Volume volume = GoogleDisks.toDaseinVolume(input, context);
			if (googleServerSupport != null) {
				includeVirtualMachines(volume);
			}
			return volume;
		}

		private void includeVirtualMachines(Volume volume) {
			try {
				Iterable<String> vmIds = googleServerSupport.getVirtualMachineNamesWithVolume(volume.getProviderVolumeId());
				// since only READ_WRITE disks are supported then it is expected the only one volume can be attached to instance
				if (vmIds.iterator().hasNext()) {
					volume.setProviderVirtualMachineId(vmIds.iterator().next());
				}
			} catch (Exception e) {
				logger.error("Failed to fetch virtual machines for volume '" + volume + "'", e);
			}
		}
	}

}
