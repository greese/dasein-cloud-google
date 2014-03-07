package org.dasein.cloud.google.util.filter;

import com.google.api.services.compute.model.Snapshot;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.dasein.cloud.compute.SnapshotFilterOptions;

/**
 * Google snapshots filters factory
 *
 * @author igoonich
 * @since 05.03.2014
 */
public class SnapshotPredicates {

	public static Predicate<Snapshot> newOptionsFilter(SnapshotFilterOptions daseinFilterOptions) {
		Preconditions.checkNotNull(daseinFilterOptions);
		if (daseinFilterOptions.isMatchesAny() || !daseinFilterOptions.hasCriteria()) {
			return Predicates.alwaysTrue();
		}
		return new SnapshotOptionsFilter(daseinFilterOptions);
	}

	/**
	 * @author igoonich
	 * @since 05.03.2014
	 */
	private static class SnapshotOptionsFilter implements Predicate<Snapshot> {
		private SnapshotFilterOptions daseinFilterOptions;

		private SnapshotOptionsFilter(SnapshotFilterOptions daseinFilterOptions) {
			this.daseinFilterOptions = daseinFilterOptions;
		}

		@Override
		public boolean apply(Snapshot googleSnapshot) {
			// tags and metadata is not supported by google, so only 'regex' is used
			if (daseinFilterOptions.getRegex() == null) {
				return true;
			}
			if (googleSnapshot.getName() != null && googleSnapshot.getName().matches(daseinFilterOptions.getRegex())) {
				return true;
			}
			return googleSnapshot.getDescription() != null && googleSnapshot.getDescription().matches(daseinFilterOptions.getRegex());
		}
	}

}
