package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.Region;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;

/**
 * @author igoonich
 * @since 12.12.2013
 */
public final class GoogleRegions {

	/**
	 * @param googleRegion google region
	 * @return dasin region
	 */
	public static org.dasein.cloud.dc.Region toDaseinRegion(Region googleRegion) {
		return GoogleToDaseinRegionConverter.getInstance().apply(googleRegion);
	}

	/**
	 * Converts an iterable collection of google regions to the collection of dasein regions
	 *
	 * @param googleRegions google regions
	 * @return dasein regions
	 */
	public static Iterable<org.dasein.cloud.dc.Region> toDaseinRegions(Iterable<Region> googleRegions) {
		return Iterables.transform(googleRegions, GoogleToDaseinRegionConverter.getInstance());
	}

	private static class GoogleToDaseinRegionConverter implements Function<Region, org.dasein.cloud.dc.Region> {

		private static final GoogleToDaseinRegionConverter INSTANCE = new GoogleToDaseinRegionConverter();

		private GoogleToDaseinRegionConverter() {
		}

		@Nullable
		@Override
		public org.dasein.cloud.dc.Region apply(@Nullable Region googleRegion) {
			org.dasein.cloud.dc.Region daseinRegion = new org.dasein.cloud.dc.Region();
			// region ID is the same as name
			daseinRegion.setProviderRegionId(googleRegion.getName());
			daseinRegion.setName(googleRegion.getName());
			daseinRegion.setActive(googleRegion.getDeprecated() == null);
			daseinRegion.setAvailable(googleRegion.getDeprecated() == null);

			return daseinRegion;
		}

		public static GoogleToDaseinRegionConverter getInstance() {
			return INSTANCE;
		}

	}

}
