package org.dasein.cloud.google.util.filter;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.apache.commons.lang.ObjectUtils;
import org.dasein.cloud.compute.VMFilterOptions;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.google.util.model.GoogleInstances;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.dasein.cloud.google.util.model.GoogleInstances.InstanceStatus;

/**
 * Google instances filters factory
 *
 * @author igoonich
 * @since 05.03.2014
 */
public final class InstancePredicates {

	public static Predicate<Instance> getOptionsFilter(VMFilterOptions vmFilterOptions) {
		Preconditions.checkNotNull(vmFilterOptions);
		if (vmFilterOptions.isMatchesAny() || !vmFilterOptions.hasCriteria()) {
			return Predicates.alwaysTrue();
		}
		Predicate<Instance> metadataFilter = newMetadataFilter(vmFilterOptions.getTags());
		Predicate<Instance> labelsFilter = newLabelsFilter(vmFilterOptions.getLabels());
		Predicate<Instance> regexpFilter = newRegexpFilter(vmFilterOptions.getRegex());
		Predicate<Instance> statesFilter = newStatesFilter(vmFilterOptions.getVmStates());

		@SuppressWarnings("unchecked")
		Predicate<Instance> vmOptionsFilter = Predicates.and(metadataFilter, labelsFilter, regexpFilter, statesFilter);

		return vmOptionsFilter;
	}

	public static Predicate<Instance> newMetadataFilter(Map<String, String> metadata) {
		if (metadata == null || metadata.isEmpty()) {
			return Predicates.alwaysTrue();
		}
		return new InstanceMetadataFilter(metadata);
	}

	public static Predicate<Instance> newLabelsFilter(@Nullable final String... expectedGoogleTags) {
		if (expectedGoogleTags == null || expectedGoogleTags.length == 0) {
			return Predicates.alwaysTrue();
		}
		return new InstanceLabelsFilter(expectedGoogleTags);
	}

	public static Predicate<Instance> newRegexpFilter(@Nullable String regexFilter) {
		if (regexFilter == null) {
			return Predicates.alwaysTrue();
		}
		return new InstanceRegexFilter(regexFilter);
	}

	public static Predicate<Instance> newStatesFilter(@Nullable Set<VmState> vmStates) {
		if (vmStates == null) {
			return Predicates.alwaysTrue();
		}
		return new InstanceStateFilter(vmStates);
	}

	/**
	 * @author igoonich
	 * @since 05.03.2014
	 */
	private static class InstanceMetadataFilter implements Predicate<Instance> {
		private Map<String, String> metadata;

		private InstanceMetadataFilter(Map<String, String> metadata) {
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

	/**
	 * @author igoonich
	 * @since 05.03.2014
	 */
	private static class InstanceLabelsFilter implements Predicate<Instance> {
		private String[] expectedGoogleTags;

		private InstanceLabelsFilter(String[] expectedGoogleTags) {
			this.expectedGoogleTags = expectedGoogleTags;
		}

		@Override
		public boolean apply(@Nullable Instance input) {
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
	}

	/**
	 * @author igoonich
	 * @since 05.03.2014
	 */
	private static class InstanceRegexFilter implements Predicate<Instance> {
		private String regexFilter;

		private InstanceRegexFilter(String regexFilter) {
			this.regexFilter = regexFilter;
		}

		@Override
		public boolean apply(Instance instance) {
			if (instance.getName() != null && instance.getName().matches(regexFilter)) {
				return true;
			}
			return instance.getDescription() != null && instance.getDescription().matches(regexFilter);
		}
	}

	/**
	 * @author igoonich
	 * @since 28.03.2014
	 */
	private static class InstanceStateFilter implements Predicate<Instance> {
		private Set<VmState> vmStates;

		private InstanceStateFilter(Set<VmState> vmStates) {
			this.vmStates = vmStates;
		}

		@Override
		public boolean apply(Instance instance) {
			InstanceStatus status = InstanceStatus.fromString(instance.getStatus());
			return vmStates.contains(status.asDaseinState());
		}
	}

}
