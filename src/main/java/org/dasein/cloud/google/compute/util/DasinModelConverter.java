package org.dasein.cloud.google.compute.util;

import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Region;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.*;

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

		// optional proeprties
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

}
