package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.Snapshot;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.SnapshotCreateOptions;

/**
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleSnapshots {

	public static Snapshot from(SnapshotCreateOptions snapshotCreateOptions, ProviderContext context) {
		return new Snapshot()
				.setName(snapshotCreateOptions.getName())
				.setDescription(snapshotCreateOptions.getDescription());
	}

}
