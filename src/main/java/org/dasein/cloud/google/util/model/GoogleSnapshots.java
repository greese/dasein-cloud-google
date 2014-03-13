package org.dasein.cloud.google.util.model;

import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.Snapshot;
import com.google.common.base.Function;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.SnapshotCreateOptions;
import org.dasein.cloud.compute.SnapshotState;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleSnapshots {

	private static final Logger logger = LoggerFactory.getLogger(GoogleSnapshots.class);

	private static final String SUCCESS = "100%";

	public enum SnapshotStatus {
		CREATING, UPLOADING, READY, DELETING, FAILED, UNKNOWN;

		public static SnapshotStatus fromString(String status) {
			try {
				return valueOf(status);
			} catch (IllegalArgumentException e) {
				logger.warn("Unknown google snapshot status [{}] will be mapped as 'UNKNOWN'", status);
				return UNKNOWN;
			}
		}

		public SnapshotState asDaseinState() {
			switch (this) {
				case CREATING:
					return SnapshotState.PENDING;
				case UPLOADING:
					return SnapshotState.PENDING;
				case READY:
					return SnapshotState.AVAILABLE;
				default:
					return SnapshotState.DELETED;
			}
		}
	}

	public static Snapshot from(SnapshotCreateOptions snapshotCreateOptions, ProviderContext context) {
		return new Snapshot()
				.setName(snapshotCreateOptions.getName())
				.setDescription(snapshotCreateOptions.getDescription());
	}

	public static org.dasein.cloud.compute.Snapshot toDaseinSnapshot(Snapshot googleSnapshot, ProviderContext context) {
		org.dasein.cloud.compute.Snapshot snapshot = new org.dasein.cloud.compute.Snapshot();

		snapshot.setRegionId(context.getRegionId());
		snapshot.setName(googleSnapshot.getName());
		snapshot.setProviderSnapshotId(googleSnapshot.getName());
		snapshot.setDescription(googleSnapshot.getDescription());

		// set source volume ID if it still available
		if (googleSnapshot.getSourceDisk() != null) {
			snapshot.setVolumeId(GoogleEndpoint.VOLUME.getResourceFromUrl(googleSnapshot.getSourceDisk()));
		}

		SnapshotStatus snapshotStatus = SnapshotStatus.fromString(googleSnapshot.getStatus());
		snapshot.setCurrentState(snapshotStatus.asDaseinState());

		snapshot.setSizeInGb(googleSnapshot.getDiskSizeGb().intValue());
		snapshot.setSnapshotTimestamp(DateTime.parseRfc3339(googleSnapshot.getCreationTimestamp()).getValue());

		// when we have google snapshot object, it means that creation operation is completely finished
		snapshot.setProgress(SUCCESS);

		return snapshot;
	}

	public static class ToDaseinSnapshotConverter implements Function<Snapshot, org.dasein.cloud.compute.Snapshot> {
		private ProviderContext providerContext;

		public ToDaseinSnapshotConverter(ProviderContext providerContext) {
			this.providerContext = providerContext;
		}

		@Override
		public @Nullable org.dasein.cloud.compute.Snapshot apply(Snapshot input) {
			return toDaseinSnapshot(input, providerContext);
		}
	}

}
