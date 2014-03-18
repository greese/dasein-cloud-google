package org.dasein.cloud.google.util.model;

import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.Image;
import com.google.common.collect.ImmutableSet;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.GoogleLogger;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * This class contains a static factory methods that allows images to be converted to Dasein objects (and vice-a-versa). This class also
 * contains various methods for manipulating google images which are not provided by default via GCE java API.
 *
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleImages {

	private static final Logger logger = GoogleLogger.getLogger(GoogleImages.class);

	/** Account for  images published by google */
	public static final String GOOGLE_IMAGES_PROJECT = "google";

	/** Account for images published by centos */
	public static final String CENTOS_IMAGES_PROJECT = "centos-cloud";

	/** Account for images published by debian */
	public static final String DEBIAN_IMAGES_PROJECT = "debian-cloud";

	/**
	 * Immutable set of possible public project which contain public images
	 */
	private static final ImmutableSet<String> PUBLIC_IMAGES_PROJECTS = ImmutableSet.of(GOOGLE_IMAGES_PROJECT,
			CENTOS_IMAGES_PROJECT, DEBIAN_IMAGES_PROJECT);


	public static @Nonnull Set<String> getPublicImagesProjects() {
		return PUBLIC_IMAGES_PROJECTS;
	}

	public enum DeprecationState {
		DEPRECATED, OBSOLETE, DELETED, UNKNOWN;

		public static DeprecationState fromString(String status) {
			try {
				return valueOf(status.toUpperCase());
			} catch (IllegalArgumentException e) {
				logger.warn("Unknown google image deprecation status [{}] will be mapped as 'UNKNOWN'", status);
				return UNKNOWN;
			}
		}
	}

	public enum ImageStatus {
		READY, FAILED, PENDING, UNKNOWN;

		public static ImageStatus fromString(String status) {
			try {
				return valueOf(status.toUpperCase());
			} catch (IllegalArgumentException e) {
				logger.warn("Unknown google image status [{}] will be mapped as 'UNKNOWN'", status);
				return UNKNOWN;
			}
		}

		public MachineImageState asDaseinState() {
			switch (this) {
				case PENDING:
					return MachineImageState.PENDING;
				case READY:
					return MachineImageState.ACTIVE;
				default:
					return MachineImageState.DELETED;
			}
		}
	}

	/**
	 * Converts google {@link Image} to dasein {@link MachineImage} object
	 *
	 * @param googleImage     google image object
	 * @param providerContext provider context
	 * @return dasein machine image instance
	 */
	public static MachineImage toDaseinImage(Image googleImage, ProviderContext providerContext) {
		MachineImage image = new MachineImage();

		// default properties
		image.setType(MachineImageType.STORAGE);
		image.setStorageFormat(MachineImageFormat.RAW);
		image.setImageClass(ImageClass.MACHINE);
		image.setArchitecture(Architecture.I64);
		image.setSoftware("unknown");

		// provider properties
		image.setProviderRegionId(providerContext.getRegionId());

		// other properties
		image.setName(googleImage.getName());
		image.setProviderMachineImageId(GoogleEndpoint.IMAGE.getResourceFromUrl(googleImage.getSelfLink()));
		image.setPlatform(Platform.guess(googleImage.getName()));

		ImageStatus imageStatus = ImageStatus.fromString(googleImage.getStatus());
		image.setCurrentState(imageStatus.asDaseinState());

		// check whether image is deprecated
		if (ImageStatus.READY.equals(imageStatus) && googleImage.getDeprecated() != null) {
			DeprecationState deprecationState = DeprecationState.fromString(googleImage.getDeprecated().getState());
			// only deprecated images are considered ACTIVE
			if (!DeprecationState.DEPRECATED.equals(deprecationState)) {
				image.setCurrentState(MachineImageState.DELETED);
			}
		}

		image.setCreationTimestamp(DateTime.parseRfc3339(googleImage.getCreationTimestamp()).getValue());
		image.setDescription(googleImage.getDescription() != null ? googleImage.getDescription()
				: googleImage.getName() + " (" + image.getArchitecture() + " " + image.getPlatform().toString() + ")");

		return image;
	}

}
