package org.dasein.cloud.google.util;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.apache.commons.lang.ObjectUtils;
import org.dasein.cloud.compute.VMFilterOptions;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory class for google predicates
 *
 * @author igoonich
 * @since 10.01.2014
 */
public final class GooglePredicates {

	private GooglePredicates() {
		throw new AssertionError();
	}

	public static Predicate<Instance> createVMOptionFilter(VMFilterOptions vmFilterOptions) {
		Preconditions.checkNotNull(vmFilterOptions);
		if (vmFilterOptions.isMatchesAny() || !vmFilterOptions.hasCriteria()) {
			return Predicates.alwaysTrue();
		}
		// TODO: make the filtering using regex from vmFilterOptions
		throw new UnsupportedOperationException("Not implemented yet");
	}

	public static Predicate<Instance> createMetadataFilter(@Nullable Map<String, String> metadata) {
		if (metadata == null || metadata.isEmpty()) {
			return Predicates.alwaysTrue();
		}
		return new MatchMetadataPredicate(metadata);
	}

	public static Predicate<Instance> createGoogleTagsFilter(@Nullable final String... expectedGoogleTags) {
		if (expectedGoogleTags == null || expectedGoogleTags.length == 0) {
			return Predicates.alwaysTrue();
		}
		return new Predicate<Instance>() {
			@Override
			public boolean apply(Instance input) {
				if (input.getTags() == null || input.getTags().getItems() == null
						|| input.getTags().getItems().isEmpty()) {
					return false;
				}
				List<String> googleTags = input.getTags().getItems();
				for (String expectedGoogleTag : expectedGoogleTags) {
					if (!googleTags.contains(expectedGoogleTag)) {
						return false;
					}
				}
				return true;
			}
		};
	}

	private static class MatchMetadataPredicate implements Predicate<Instance> {

		private Map<String, String> metadata;

		public MatchMetadataPredicate(Map<String, String> metadata) {
			Preconditions.checkArgument(metadata != null && !metadata.isEmpty(), "metadata is empty");
			this.metadata = metadata;
		}

		@Override
		public boolean apply(Instance input) {
			if (input.getMetadata() == null || input.getMetadata().getItems() == null
					|| input.getMetadata().getItems().isEmpty()) {
				return false;
			}
			Map<String, String> currentMetadata = getMetadataAsMap(input.getMetadata().getItems());
			for (String key : metadata.keySet()) {
				if (!currentMetadata.containsKey(key) || (!ObjectUtils.equals(metadata.get(key), currentMetadata.get(key)))) {
					return false;
				}
			}
			return true;
		}

		private static Map<String, String> getMetadataAsMap(List<Metadata.Items> itemsList) {
			Map<String, String> metadataMap = new HashMap<String, String>(itemsList.size(), 1.0f);
			for (Metadata.Items keyValuePair : itemsList) {
				metadataMap.put(keyValuePair.getKey(), keyValuePair.getValue());
			}
			return metadataMap;
		}

	}
}
