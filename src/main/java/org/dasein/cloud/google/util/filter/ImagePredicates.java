package org.dasein.cloud.google.util.filter;

import com.google.api.services.compute.model.Image;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.dasein.cloud.compute.ImageFilterOptions;
import org.dasein.cloud.compute.MachineImage;

import javax.annotation.Nullable;

/**
 * Google images filters factory
 *
 * @author igoonich
 * @since 14.03.2014
 */
public class ImagePredicates {

	public static Predicate<Image> getOptionsFilter(ImageFilterOptions imageFilterOptions) {
		if (imageFilterOptions.isMatchesAny() || !imageFilterOptions.hasCriteria()) {
			return Predicates.alwaysTrue();
		}
		return Predicates.alwaysTrue();
	}

	/*
	// TODO
	private static class ImagesOptionsFilter implements Predicate<Image> {

		private ImageFilterOptions imageFilterOptions;

		private ImagesOptionsFilter(ImageFilterOptions imageFilterOptions) {
			this.imageFilterOptions = imageFilterOptions;
		}

		@Override
		public boolean apply(@Nullable Image input) {
			if (input.getStatus().equals(imageFilterOptions.getArchitecture())) {
				return true;
			}
			return false;
		}
	}
	*/


}
