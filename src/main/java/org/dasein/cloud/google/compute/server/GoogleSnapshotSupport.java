/**
 * Copyright (C) 2012-2013 Dell, Inc
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.google.compute.server;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.SnapshotList;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.compute.AbstractSnapshotSupport;
import org.dasein.cloud.compute.Snapshot;
import org.dasein.cloud.compute.SnapshotCreateOptions;
import org.dasein.cloud.compute.SnapshotFilterOptions;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.GoogleLogger;
import org.dasein.cloud.google.util.filter.SnapshotPredicates;
import org.dasein.cloud.google.util.model.GoogleSnapshots;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

import static com.google.api.client.util.Preconditions.checkNotNull;

/**
 * Implements the snapshot services supported in the Google API.
 *
 * @author igoonich
 * @since 2013.01
 */
public class GoogleSnapshotSupport extends AbstractSnapshotSupport {

	private static final Logger logger = GoogleLogger.getLogger(GoogleSnapshotSupport.class);

	private Google provider;
	private OperationSupport<Operation> operationSupport;

	public GoogleSnapshotSupport(Google provider) {
		super(provider);
		this.provider = provider;
		this.operationSupport = provider.getComputeServices().getOperationsSupport();
	}

	@Override
	public String createSnapshot(SnapshotCreateOptions options) throws CloudException, InternalException {
		checkNotNull(options, "snapshot creation options are not provided");

		// submit create operation in background
		Operation operation = submitSnapshotCreationOperation(options);
		operationSupport.waitUntilOperationCompletes(operation);

		return options.getName();
	}

	/**
	 * Submits google snapshot creation command from google disk to GCE
	 *
	 * @param options google snapshot options
	 * @return operation scheduled on google side
	 * @throws CloudException in case of any errors
	 */
	protected @Nonnull Operation submitSnapshotCreationOperation(SnapshotCreateOptions options) throws CloudException {
		checkNotNull(options.getVolumeId(), "volume ID is not provided");

		GoogleDiskSupport googleDiskSupport = provider.getComputeServices().getVolumeSupport();
		Disk googleDisk = googleDiskSupport.findDisk(options.getVolumeId(), provider.getContext().getAccountNumber(),
				provider.getContext().getRegionId());
		if (googleDisk == null) {
			throw new CloudException("Volume with ID [" + options.getVolumeId() + "] doesn't exist");
		}

		com.google.api.services.compute.model.Snapshot googleSnapshot = GoogleSnapshots.from(options, provider.getContext());
		return submitSnapshotCreationOperation(googleSnapshot, googleDisk);
	}

	/**
	 * Submits google snapshot creation command from google disk to GCE
	 *
	 * @param googleSnapshot google snapshot to be created
	 * @param googleDisk     google disk to be used as a source
	 * @return operation scheduled on google side
	 * @throws CloudException in case of any errors
	 */
	protected @Nonnull Operation submitSnapshotCreationOperation(com.google.api.services.compute.model.Snapshot googleSnapshot,
																 Disk googleDisk) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Disks.CreateSnapshot createSnapshotRequest = compute.disks().createSnapshot(provider.getContext().getAccountNumber(),
					GoogleEndpoint.ZONE.getResourceFromUrl(googleDisk.getZone()), googleDisk.getName(), googleSnapshot);
			return createSnapshotRequest.execute();
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to create snapshot [" + googleSnapshot.getName() + "] from disk ["
				+ googleDisk.getName() + "]");
	}

	@Override
	public Snapshot getSnapshot(String snapshotId) throws InternalException, CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Snapshots.Get getSnapshotRequest = compute.snapshots().get(provider.getContext().getAccountNumber(), snapshotId);
			com.google.api.services.compute.model.Snapshot googleSnapshot = getSnapshotRequest.execute();
			return GoogleSnapshots.toDaseinSnapshot(googleSnapshot, provider.getContext());
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public @Nonnull Iterable<Snapshot> searchSnapshots(SnapshotFilterOptions options)
			throws InternalException, CloudException {
		checkNotNull(options, "snapshot filter options are not provided");
		// GCE snapshots doesn't have field which identify who created this snapshot
		return listSnapshots();
	}

	@Override
	public @Nonnull Iterable<Snapshot> listSnapshots() throws InternalException, CloudException {
		return listSnapshots(SnapshotFilterOptions.getInstance());
	}

	@Override
	public @Nonnull Iterable<Snapshot> listSnapshots(SnapshotFilterOptions options) throws InternalException, CloudException {
		checkNotNull(options, "snapshot filter options are not provided");
		Predicate<com.google.api.services.compute.model.Snapshot> optionsFilter = SnapshotPredicates.newOptionsFilter(options);
		return listSnapshots(new GoogleSnapshots.ToDaseinSnapshotConverter(provider.getContext()), optionsFilter);
	}

	/**
	 * Generic method which produces a list of objects using a converting function from google snapshots
	 *
	 * @param snapshotConverter google snapshot converting function
	 * @param snapshotsFilter   google snapshot filtering predicate
	 * @param <T>               producing result type of {@code snapshotConverter}
	 * @return list of snapshots
	 * @throws CloudException in case any error occurred within the cloud provider
	 */
	protected @Nonnull <T> Iterable<T> listSnapshots(Function<com.google.api.services.compute.model.Snapshot, T> snapshotConverter,
													 Predicate<com.google.api.services.compute.model.Snapshot> snapshotsFilter) throws CloudException {
		checkNotNull(snapshotConverter, "snapshots converter is not provided");
		checkNotNull(snapshotsFilter, "snapshots filter is not provided");

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Snapshots.List listSnapshotsRequest = compute.snapshots().list(provider.getContext().getAccountNumber());
			SnapshotList snapshotList = listSnapshotsRequest.execute();

			if (snapshotList == null || snapshotList.getItems() == null) {
				return Collections.emptyList();
			}

			return Iterables.transform(Iterables.filter(snapshotList.getItems(), snapshotsFilter), snapshotConverter);
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		return Collections.emptyList();
	}

	@Override
	public void remove(String snapshotId) throws InternalException, CloudException {
		checkNotNull(snapshotId, "snapshot ID is not provided");
		// submit delete operation in background
		Operation operation = submitSnapshotDeleteOperation(snapshotId);
		operationSupport.waitUntilOperationCompletes(operation);
	}

	protected @Nonnull Operation submitSnapshotDeleteOperation(String snapshotId) throws CloudException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Snapshots.Delete deleteSnapshotRequest = compute.snapshots().delete(provider.getContext().getAccountNumber(), snapshotId);
			return deleteSnapshotRequest.execute();
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		throw new IllegalStateException("Failed to delete snapshot [" + snapshotId + "]");
	}

	@Override
	public String getProviderTermForSnapshot(Locale locale) {
		return "snapshot";
	}

	@Override
	public boolean isSubscribed() throws InternalException, CloudException {
		return true;
	}

}
