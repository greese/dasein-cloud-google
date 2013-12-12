package org.dasein.cloud.google.util;

import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Zone;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.compute.server.GoogleServerSupport;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;

/**
 * Utility class for converting resource objects between Dasin and Google API models
 *
 * @author igoonich
 * @since 10.12.2013
 */
public final class DasinModelConverter {

	private DasinModelConverter() {
		throw new AssertionError();
	}

	/**
	 * @param googleRegion google region object
	 * @return dasin region object
	 */
	public static org.dasein.cloud.dc.Region from(Region googleRegion) {
		org.dasein.cloud.dc.Region dasinRegion = new org.dasein.cloud.dc.Region();

		// reqion ID is the same as name
		dasinRegion.setProviderRegionId(googleRegion.getName());
		dasinRegion.setName(googleRegion.getName());
		dasinRegion.setActive(googleRegion.getDeprecated() == null);
		dasinRegion.setAvailable(googleRegion.getDeprecated() == null);

		return dasinRegion;
	}

	public static Region from(org.dasein.cloud.dc.Region dasinRegion) {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * @param googleImage     google image object
	 * @param providerContext provider context
	 * @return dasin machine image instance
	 */
	public static MachineImage from(Image googleImage, ProviderContext providerContext) {
		MachineImage image = new MachineImage();

		// default properties
		image.setType(MachineImageType.STORAGE);
		image.setStorageFormat(MachineImageFormat.RAW);
		image.setCurrentState(MachineImageState.ACTIVE);
		image.setImageClass(ImageClass.MACHINE);
		image.setArchitecture(Architecture.I32);

		// provider properties
		image.setProviderRegionId(providerContext.getRegionId());

		// converted properties
		image.setName(googleImage.getName());
		image.setProviderMachineImageId(googleImage.getName());
		image.setPlatform(Platform.guess(googleImage.getName()));
		if ("PENDING".equalsIgnoreCase(googleImage.getStatus())) {
			image.setCurrentState(MachineImageState.PENDING);
		} else {
			// google "DEPRECATED" --> dasin "ACTIVE"
			// google "OBSOLETE" --> dasin "DELETED"
			// google "DELETED" --> dasin "DELETED"
			image.setCurrentState(googleImage.getDeprecated().getState() != null && !"DEPRECATED".equals(googleImage.getDeprecated().getState())
					? MachineImageState.DELETED : MachineImageState.ACTIVE);
		}
		image.setCreationTimestamp(DateTime.parseRfc3339(googleImage.getCreationTimestamp()).getValue());
		image.setDescription(googleImage.getDescription() != null ? googleImage.getDescription()
				: googleImage.getName() + " (" + image.getArchitecture() + " " + image.getPlatform().toString() + ")");

		// TODO: check weather "preferredKernel" and "software" are needed
		image.setKernelImageId(googleImage.getKind());
		image.setSoftware("");

		return image;
	}

	public static DataCenter from(Zone googleDataCenter) {
		DataCenter dataCenter = new DataCenter();
		dataCenter.setActive(true);
		dataCenter.setAvailable(true);
		dataCenter.setName(googleDataCenter.getName());
		dataCenter.setProviderDataCenterId(googleDataCenter.getName());
		dataCenter.setRegionId(googleDataCenter.getRegion());
		return dataCenter;
	}

	public static Volume from(Disk googleVolume, Google provider) throws CloudException {
		Volume volume = new Volume();

		// default properties
		volume.setType(VolumeType.HDD);

		// complex properties
		volume.setProviderRegionId(provider.getContext().getRegionId());

		volume.setProviderVolumeId(googleVolume.getName());
		volume.setName(googleVolume.getName());
		volume.setDescription(googleVolume.getDescription());
		volume.setSize(new Storage<Gigabyte>(googleVolume.getSizeGb(), Storage.GIGABYTE));

		// TODO: check. old version - vol.setProviderSnapshotId(GoogleMethod.getResourceName(json.getString("sourceSnapshot"), GoogleMethod.SNAPSHOT));
		volume.setProviderSnapshotId(googleVolume.getSourceSnapshotId());

		// TODO: check. old version - vol.setProviderDataCenterId(GoogleMethod.getResourceName(json.getString("zone"), GoogleMethod.ZONE));
		volume.setProviderDataCenterId(googleVolume.getZone() != null ? StringUtils.substringAfterLast(googleVolume.getZone(), "/") : null);
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
			Iterable<String> vmIds = virtualMachineSupport.getVirtualMachineWithVolume(volume.getProviderVolumeId());
			if (vmIds != null) volume.setProviderVirtualMachineId(vmIds.iterator().next());
		} catch (InternalException e) {
			throw new CloudException(e);
		}

		return volume;
	}

}
