/**
 * Copyright (C) 2012-2014 Dell, Inc
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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.compute.Snapshot;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCESnapshotCapabilities;
import org.dasein.cloud.util.APITrace;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * Implements the snapshot services supported in the Google API.
 * @author Drew Lyall
 * @version 2014.03 initial version
 * @since 2014.03
 */

public class SnapshotSupport extends AbstractSnapshotSupport{
    static private final Logger logger = Google.getLogger(SnapshotSupport.class);
    private Google provider;

    public SnapshotSupport(Google provider){
        super(provider);
        this.provider = provider;
    }

    @Override
    public void addSnapshotShare(@Nonnull String providerSnapshotId, @Nonnull String accountNumber) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google does not support sharing a snapshot across accounts.");

    }

    @Override
    public void addPublicShare(@Nonnull String providerSnapshotId) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google does not support sharing a snapshot across accounts.");
    }

    @Override
    public String createSnapshot(@Nonnull SnapshotCreateOptions options) throws CloudException, InternalException{
        APITrace.begin(provider, "Snapshot.createSnapshot");
        try{
            Compute gce = provider.getGoogleCompute();
            try{
                Volume volume = provider.getComputeServices().getVolumeSupport().getVolume(options.getVolumeId());

                com.google.api.services.compute.model.Snapshot snapshot = new com.google.api.services.compute.model.Snapshot();
                snapshot.setName(options.getName());
                snapshot.setDescription(options.getDescription());
                snapshot.setSourceDiskId(options.getVolumeId());

                Operation job = gce.disks().createSnapshot(provider.getContext().getAccountNumber(), volume.getProviderDataCenterId(), options.getVolumeId(), snapshot).execute();
                GoogleMethod method = new GoogleMethod(provider);
                if(method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", volume.getProviderDataCenterId())){
                    SnapshotList snapshots = gce.snapshots().list(provider.getContext().getAccountNumber()).setFilter("name eq " + options.getName()).execute();
                    for(com.google.api.services.compute.model.Snapshot s : snapshots.getItems()){
                        if(s.getName().equals(options.getName()))return s.getName();
                    }
                }
                throw new CloudException("An error occurred creating the snapshot: Operation Timedout");
    	    } catch (IOException ex) {
                logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred creating the snapshot: " + ex.getMessage());
    		} catch (Exception ex) {
    		    throw new OperationNotSupportedException("Copying snapshots is not supported in GCE");
    		}
        }
        finally {
            APITrace.end();
        }
    }

    private transient volatile GCESnapshotCapabilities capabilities;
    @Override
    public @Nonnull GCESnapshotCapabilities getCapabilities(){
        if(capabilities == null){
            capabilities = new GCESnapshotCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public @Nonnull String getProviderTermForSnapshot(@Nonnull Locale locale){
        return "snapshot";
    }

    @Override
    public Snapshot getSnapshot(@Nonnull String snapshotId) throws InternalException, CloudException{
        APITrace.begin(provider, "Snapshot.getSnapshot");
        try{
            Compute gce = provider.getGoogleCompute();
            try{
                com.google.api.services.compute.model.Snapshot snapshot = gce.snapshots().get(provider.getContext().getAccountNumber(), snapshotId).execute();
                return toSnapshot(snapshot);
    	    } catch (IOException ex) {
    	        if ((ex.getMessage() != null) && (ex.getMessage().contains("404 Not Found"))) // not found.
    	            return null;
                logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred getting the snapshot: " + ex.getMessage());
    		}
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public boolean isPublic(@Nonnull String snapshotId) throws InternalException, CloudException{
        return false;
    }

    @Override
    public boolean isSubscribed() throws InternalException, CloudException{
        return true;
    }

    @Override
    public @Nonnull Iterable<String> listShares(@Nonnull String snapshotId) throws InternalException, CloudException{
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listSnapshotStatus() throws InternalException, CloudException{
        APITrace.begin(provider, "Snapshot.listSnapshotStatus");
        try{
            ArrayList<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
            Compute gce = provider.getGoogleCompute();
            try{
                SnapshotList list = gce.snapshots().list(provider.getContext().getAccountNumber()).execute();
                if(list != null && list.size() > 0){
                    for(com.google.api.services.compute.model.Snapshot googleSnapshot : list.getItems()){
                        ResourceStatus status = toStatus(googleSnapshot);
                        if(status != null)statuses.add(status);
                    }
                }
                return statuses;
    	    } catch (IOException ex) {
                logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred retrieving snapshot status");
    		}
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<Snapshot> listSnapshots() throws InternalException, CloudException{
        APITrace.begin(provider, "Snapshot.listSnapshots");
        try{
            ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();
            Compute gce = provider.getGoogleCompute();
            try{
                SnapshotList list = gce.snapshots().list(provider.getContext().getAccountNumber()).execute();
                if(list != null && list.getItems() != null && list.getItems().size() > 0){
                    for(com.google.api.services.compute.model.Snapshot googleSnapshot : list.getItems()){
                        Snapshot snapshot = toSnapshot(googleSnapshot);
                        if(snapshot != null)snapshots.add(snapshot);
                    }
                }
                return snapshots;
    	    } catch (IOException ex) {
                logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred while listing snapshots: " + ex.getMessage());
    		}
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<Snapshot> listSnapshots(SnapshotFilterOptions options) throws InternalException, CloudException{
        return searchSnapshots(options);
    }

    @Override
    public void remove(@Nonnull String snapshotId) throws InternalException, CloudException{
        APITrace.begin(provider, "Snapshot.remove");
        try{
            Compute gce = provider.getGoogleCompute();
            try{
                Operation job = gce.snapshots().delete(provider.getContext().getAccountNumber(), snapshotId).execute();

                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "")){
                    throw new CloudException("An error occurred deleting the snapshot: Operation timed out");
                }
    	    } catch (IOException ex) {
    			if (ex.getClass() == GoogleJsonResponseException.class) {
                    logger.error(ex.getMessage());
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred deleting the snapshot: " + ex.getMessage());
    		}
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void removeAllSnapshotShares(@Nonnull String providerSnapshotId) throws CloudException, InternalException{
        // NOP in clouds without sharing
    }

    @Override
    public void removeSnapshotShare(@Nonnull String providerSnapshotId, @Nonnull String accountNumber) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google does not support sharing/unsharing a snapshot across accounts.");

    }

    @Override
    public void removePublicShare(@Nonnull String providerSnapshotId) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google does not support sharing/unsharing a snapshot across accounts.");

    }

    @Override
    public void removeTags(@Nonnull String snapshotId, @Nonnull Tag... tags) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google snapshot does not contain meta data");

    }

    @Override
    public void removeTags(@Nonnull String[] snapshotIds, @Nonnull Tag... tags) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google snapshot does not contain meta data");
    }

    @Override
    public @Nonnull Iterable<Snapshot> searchSnapshots(@Nonnull SnapshotFilterOptions options) throws InternalException, CloudException{
        APITrace.begin(provider, "Snapshot.searchSnapshots");
        try{
            ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();
            for(Snapshot snapshot : listSnapshots()){
                if(options == null || options.matches(snapshot, null)){
                    snapshots.add(snapshot);
                }
            }
            return snapshots;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void updateTags(@Nonnull String snapshotId, @Nonnull Tag... tags) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google snapshot does not contain meta data");
    }

    @Override
    public void updateTags(@Nonnull String[] snapshotIds, @Nonnull Tag... tags) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google snapshot does not contain meta data");
    }

    private @Nullable Snapshot toSnapshot(com.google.api.services.compute.model.Snapshot googleSnapshot){
        Snapshot snapshot = new Snapshot();
        snapshot.setProviderSnapshotId(googleSnapshot.getName());
        snapshot.setName(googleSnapshot.getName());
        snapshot.setDescription(googleSnapshot.getDescription());
        snapshot.setOwner(provider.getContext().getAccountNumber());
        SnapshotState state = SnapshotState.PENDING;
        if(googleSnapshot.getStatus().equals("READY"))state = SnapshotState.AVAILABLE;
        else if(googleSnapshot.getStatus().equals("DELETING"))state = SnapshotState.DELETED;
        snapshot.setCurrentState(state);
        //TODO: Set visible scope for snapshots
        snapshot.setSizeInGb(googleSnapshot.getDiskSizeGb().intValue());

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse(googleSnapshot.getCreationTimestamp(), fmt);
        snapshot.setSnapshotTimestamp(dt.toDate().getTime());

        String sourceDisk = googleSnapshot.getSourceDisk();
        if (sourceDisk != null) {
            snapshot.setVolumeId(sourceDisk.substring(sourceDisk.lastIndexOf("/") + 1));
        }

        return snapshot;
    }

    private @Nullable ResourceStatus toStatus(@Nullable com.google.api.services.compute.model.Snapshot snapshot) throws CloudException {
        SnapshotState state;
        if(snapshot.getStatus().equals("READY")){
            state = SnapshotState.AVAILABLE;
        }
        else if(snapshot.getStatus().equals("DELETING")){
            state = SnapshotState.DELETED;
        }
        else state = SnapshotState.PENDING;

        return new ResourceStatus(snapshot.getName(), state);
    }
}
