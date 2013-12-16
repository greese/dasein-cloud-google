package org.dasein.cloud.google.util.model;

import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.Image;
import com.google.common.collect.ImmutableSet;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.*;

import javax.annotation.Nonnull;

/**
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleImages {

	/**
	 * Images published by google
	 */
	public static final String GOOGLE_IMAGES_PROJECT = "google";

	/**
	 * Images published by centos
	 */
	public static final String CENTOS_IMAGES_PROJECT = "centos-cloud";

	/**
	 * Images published by debian
	 */
	public static final String DEBIAN_IMAGES_PROJECT = "debian-cloud";

	private static final ImmutableSet<String> PUBLIC_IMAGES_PROJECTS = ImmutableSet.of(GOOGLE_IMAGES_PROJECT,
			CENTOS_IMAGES_PROJECT, DEBIAN_IMAGES_PROJECT);

	@Nonnull
	public static ImmutableSet<String> getPublicImagesProjects() {
		return PUBLIC_IMAGES_PROJECTS;
	}

	/**
	 * @param googleImage     google image object
	 * @param providerContext provider context
	 * @return dasein machine image instance
	 */
	public static MachineImage toDaseinImage(Image googleImage, ProviderContext providerContext) {
		MachineImage image = new MachineImage();

		// default properties
		image.setType(MachineImageType.STORAGE);
		image.setStorageFormat(MachineImageFormat.RAW);
		image.setCurrentState(MachineImageState.ACTIVE);
		image.setImageClass(ImageClass.MACHINE);
		image.setArchitecture(Architecture.I64);

		// provider properties
		image.setProviderRegionId(providerContext.getRegionId());

		// converted properties
		image.setName(googleImage.getName());
		image.setProviderMachineImageId(googleImage.getName());
		image.setPlatform(Platform.guess(googleImage.getName()));
		if ("PENDING".equalsIgnoreCase(googleImage.getStatus())) {
			image.setCurrentState(MachineImageState.PENDING);
		} else {
			// google "DEPRECATED" --> dasein "ACTIVE"
			// google "OBSOLETE" --> dasein "DELETED"
			// google "DELETED" --> daesein "DELETED"
			image.setCurrentState(googleImage.getDeprecated() != null && !"DEPRECATED".equals(googleImage.getDeprecated().getState())
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
