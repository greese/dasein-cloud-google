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

import com.google.api.client.util.Preconditions;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Operation;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleMethod.Param;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.compute.GoogleCompute;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.model.GoogleSnapshots;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.util.CalendarWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * Implements the snapshot services supported in the Google API.
 *
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleSnapshotSupport implements SnapshotSupport {
	private static final Logger logger = Google.getLogger(GoogleSnapshotSupport.class);
	private Google provider;

	public GoogleSnapshotSupport(Google provider) {
		this.provider = provider;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public void addSnapshotShare(String providerSnapshotId, String accountNumber)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support sharing a snapshot across accounts.");

	}

	@Override
	public void addPublicShare(String providerSnapshotId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support sharing a snapshot across accounts.");
	}

	@Override
	public Snapshot snapshot(String volumeId, String name, String description, Tag... tags) throws InternalException, CloudException {
		Preconditions.checkNotNull(volumeId);
		Preconditions.checkNotNull(name);

		SnapshotCreateOptions options = SnapshotCreateOptions.getInstanceForCreate(volumeId, name, description);
		Operation operation = submitSnapshotCreationOperation(options);

		OperationSupport<Operation> operationSupport = provider.getComputeServices().getOperationsSupport();
		operationSupport.waitUntilOperationCompletes(operation, 30);

		return getSnapshot(name);
	}

	@Override
	public String createSnapshot(SnapshotCreateOptions options) throws CloudException, InternalException {
		// submit create operation in background
		submitSnapshotCreationOperation(options);
		return options.getName();
	}

	/**
	 * Submits google snapshot creation command from google disk to GCE
	 *
	 * @param options google snapshot options
	 * @return operation scheduled on google side
	 * @throws CloudException in case of any errors
	 */
	@Nonnull
	protected Operation submitSnapshotCreationOperation(SnapshotCreateOptions options) throws CloudException {
		Preconditions.checkNotNull(options.getVolumeId(), "Volume ID is not provided");

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
	@Nonnull
	protected Operation submitSnapshotCreationOperation(com.google.api.services.compute.model.Snapshot googleSnapshot,
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
	public String create(String ofVolume, String description)
			throws InternalException, CloudException {
		SnapshotCreateOptions options = SnapshotCreateOptions.getInstanceForCreate(ofVolume, null, description);
		return createSnapshot(options);
	}

	@Override
	public String getProviderTermForSnapshot(Locale locale) {
		return "snapshot";
	}

	@Override
	public Snapshot getSnapshot(String snapshotId) throws InternalException, CloudException {

		GoogleMethod method = new GoogleMethod(provider);
		JSONArray list = method.get(GoogleMethod.SNAPSHOT + "/" + snapshotId);

		if (list == null) {
			return null;
		}

		for (int i = 0; i < list.length(); i++) {
			try {
				Snapshot snap = toSnapshot(list.getJSONObject(i));
				if (snap != null && snap.getProviderSnapshotId().equals(snapshotId)) {
					return snap;
				}
			} catch (JSONException e) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}

		return null;
	}

	private
	@Nullable
	Snapshot toSnapshot(JSONObject json) throws CloudException {
		if (json == null) {
			return null;
		}
		Snapshot snapshot = new Snapshot();
		snapshot.setRegionId(provider.getContext().getRegionId());
		try {

			if (json.has("name")) {
				snapshot.setName(json.getString("name"));
				snapshot.setProviderSnapshotId(json.getString("name"));
			}
			if (json.has("description")) {
				snapshot.setDescription(json.getString("description"));
			}
			if (json.has("sourceDisk")) {
				snapshot.setVolumeId(json.getString("sourceDisk"));
			}
			if (json.has("status")) {
				String status = json.getString("status");
				SnapshotState state;
				if (status.equals("CREATING")) {
					state = SnapshotState.PENDING;
				} else if (status.equals("UPLOADING")) {
					state = SnapshotState.PENDING;
				} else if (status.equals("READY")) {
					state = SnapshotState.AVAILABLE;
				} else {
					state = SnapshotState.DELETED;
				}
				snapshot.setCurrentState(state);
			}

			if (json.has("diskSizeGb")) {
				int size = Integer.parseInt(json.getString("diskSizeGb"));
				snapshot.setSizeInGb(size);
			} else snapshot.setSizeInGb(0);

			if (snapshot.getDescription() == null) {
				snapshot.setDescription(snapshot.getName() + " [" + snapshot.getSizeInGb() + " GB]");
			}
			if (snapshot.getSizeInGb() < 1) {

				GoogleCompute svc = provider.getComputeServices();

				if (svc != null) {
					VolumeSupport vs = svc.getVolumeSupport();
					try {
						Volume vol = vs.getVolume(snapshot.getVolumeId());

						if (vol != null) {
							snapshot.setSizeInGb(vol.getSizeInGigabytes());
						}
					} catch (InternalException ignore) {
						// ignore
					}
				}
			}
		} catch (JSONException e) {
			logger.error("Failed to parse JSON from the cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}
		return snapshot;
	}

	@Override
	public Requirement identifyAttachmentRequirement()
			throws InternalException, CloudException {
		return Requirement.OPTIONAL;
	}

	@Override
	public boolean isPublic(String snapshotId) throws InternalException,
			CloudException {
		return false;
	}

	@Override
	public boolean isSubscribed() throws InternalException, CloudException {
		return true;
	}

	@Override
	public Iterable<String> listShares(String snapshotId)
			throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<ResourceStatus> listSnapshotStatus()
			throws InternalException, CloudException {
		List<ResourceStatus> status = new ArrayList<ResourceStatus>();

		Iterable<Snapshot> snapshots = listSnapshots();
		for (Snapshot snapshot : snapshots) {
			SnapshotState state = snapshot.getCurrentState();
			ResourceStatus resStatus = new ResourceStatus(snapshot.getProviderSnapshotId(), state);
			status.add(resStatus);
		}
		return status;
	}

	@Override
	public Iterable<Snapshot> listSnapshots() throws InternalException, CloudException {
		return listSnapshots(SnapshotFilterOptions.getInstance());
	}

	@Override
	public Iterable<Snapshot> listSnapshots(SnapshotFilterOptions options)
			throws InternalException, CloudException {
		GoogleMethod method = new GoogleMethod(provider);

		Param param = new Param("filter", options.getRegex());
		JSONArray list = method.get(GoogleMethod.SNAPSHOT, param);

		if (list == null) {
			return Collections.emptyList();
		}
		ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();
		for (int i = 0; i < list.length(); i++) {
			try {
				Snapshot snap = toSnapshot(list.getJSONObject(i));

				if (snap != null) {
					snapshots.add(snap);
				}
			} catch (JSONException e) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}
		return snapshots;
	}

	@Override
	public void remove(String snapshotId) throws InternalException,
			CloudException {
		GoogleMethod method = new GoogleMethod(provider);
		method.delete(GoogleMethod.SNAPSHOT, new GoogleMethod.Param("id", snapshotId));

		long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 15L);

		while (timeout > System.currentTimeMillis()) {
			Snapshot snap = getSnapshot(snapshotId);

			if (snap == null || snap.getCurrentState().equals(SnapshotState.DELETED)) {
				return;
			}
			try {
				Thread.sleep(15000L);
			} catch (InterruptedException ignore) {
			}
		}
		throw new CloudException("Snapshot deletion failed !");

	}

	@Override
	public void removeAllSnapshotShares(String providerSnapshotId)
			throws CloudException, InternalException {
		// NO OP
	}

	@Override
	public void removeSnapshotShare(String providerSnapshotId,
									String accountNumber) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support sharing/unsharing a snapshot across accounts.");

	}

	@Override
	public void removePublicShare(String providerSnapshotId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support sharing/unsharing a snapshot across accounts.");

	}

	@Override
	public void removeTags(String snapshotId, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google snapshot does not contain meta data");

	}

	@Override
	public void removeTags(String[] snapshotIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google snapshot does not contain meta data");

	}

	@Override
	public Iterable<Snapshot> searchSnapshots(SnapshotFilterOptions arg0)
			throws InternalException, CloudException {
		List<Snapshot> searchSnapshots = new ArrayList<Snapshot>();

		Iterable<Snapshot> snapshots = listSnapshots();
		Map<String, String> tag = arg0.getTags();

		for (Snapshot snapshot : snapshots) {
			for (Entry<String, String> entry : tag.entrySet()) {
				String keyword = entry.getValue();
				if (keyword.equals(snapshot.getProviderSnapshotId()) || !snapshot.getName().contains(keyword) || !snapshot.getDescription().contains(keyword)) {
					searchSnapshots.add(snapshot);
					break;
				}
			}
		}
		return searchSnapshots;
	}

	@Override
	public Iterable<Snapshot> searchSnapshots(String ownerId, String keyword)
			throws InternalException, CloudException {
		List<Snapshot> searchSnapshots = new ArrayList<Snapshot>();

		Iterable<Snapshot> snapshots = listSnapshots();
		for (Snapshot snapshot : snapshots) {
			if (ownerId != null && !ownerId.equals(snapshot.getProviderSnapshotId())) {
				continue;
			}
			if (keyword != null && !snapshot.getName().contains(keyword) && !snapshot.getDescription().contains(keyword)) {
				continue;
			}
			searchSnapshots.add(snapshot);
		}
		return searchSnapshots;
	}

	@Override
	public void shareSnapshot(String snapshotId, String withAccountId, boolean affirmative) throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support sharing/unsharing a snapshot across accounts.");
	}

	@Override
	public boolean supportsSnapshotCopying() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public boolean supportsSnapshotCreation() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean supportsSnapshotSharing() throws InternalException,
			CloudException {
		return false;
	}

	@Override
	public boolean supportsSnapshotSharingWithPublic()
			throws InternalException, CloudException {
		return false;
	}

	@Override
	public void updateTags(String snapshotId, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google snapshot does not contain meta data");

	}

	@Override
	public void updateTags(String[] snapshotIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google snapshot does not contain meta data");

	}

}
