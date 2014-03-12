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

import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleMethod.Param;
import org.dasein.cloud.google.capabilities.GCESnapshotCapabilities;
import org.dasein.cloud.google.compute.GoogleCompute;
import org.dasein.util.CalendarWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;

/**
 * Implements the snapshot services supported in the Google API.
 * @author Andy Lyall
 * @version 2014.03 initial version
 * @since 2014.03
 */

public class SnapshotSupport extends AbstractSnapshotSupport{
    static private final Logger logger = Google.getLogger(SnapshotSupport.class);
    private Google provider;

    public SnapshotSupport(Google provider){
        super(provider);
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

        GoogleMethod method = new GoogleMethod(provider);
        JSONObject payload = new JSONObject();
        ProviderContext ctx = provider.getContext();

        try{
            String name = options.getName();
            String ofVolume = options.getVolumeId();

            if(options.getName() != null) name = options.getName();
            else name = "snap" + ofVolume;

            if(name.length() > 63) name = name.substring(0, 62);

            name = name.replace(" ", "").replace("-", "").replace(":", "").toLowerCase();

            payload.put("name", name);

            if(options.getDescription() != null) payload.put("description", options.getDescription());
            else payload.put("description", name);

            ofVolume = method.getEndpoint(ctx, GoogleMethod.VOLUME) + "/" + ofVolume;
            payload.put("sourceDisk", ofVolume);

        }catch(JSONException e){
            e.printStackTrace();
            logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
            throw new CloudException(e);
        }

        JSONObject response = null;
        try{
            response = method.post(GoogleMethod.SNAPSHOT, payload);
        }catch(GoogleException e){
            e.printStackTrace();
            logger.error(e.getLocalizedMessage());
            throw new CloudException(e);
        }

        String resName = null;
        String status = method.getOperationStatus(GoogleMethod.GLOBAL_OPERATION, response);
        if(status.equals("DONE")){
            if(response.has("targetLink")){
                try{
                    resName = response.getString("targetLink");
                }catch(JSONException e){
                    e.printStackTrace();
                    logger.error("JSON parser failed");
                    throw new CloudException(e);
                }
                resName = GoogleMethod.getResourceName(resName, GoogleMethod.SNAPSHOT);
                return resName;
            }
        }
        return null;
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

        snapshotId = snapshotId.replace(" ", "").replace("-", "").replace(":", "");

        GoogleMethod method = new GoogleMethod(provider);
        JSONArray list = method.get(GoogleMethod.SNAPSHOT + "/" + snapshotId);

        if(list == null){
            return null;
        }

        for(int i = 0; i < list.length(); i++){
            try{
                Snapshot snap = toSnapshot(list.getJSONObject(i));
                if(snap != null && snap.getProviderSnapshotId().equals(snapshotId)){
                    return snap;
                }
            }catch(JSONException e){
                logger.error("Failed to parse JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(e);
            }
        }
        return null;
    }

    private @Nullable Snapshot toSnapshot(JSONObject json) throws CloudException{
        if(json == null){
            return null;
        }
        Snapshot snapshot = new Snapshot();
        snapshot.setRegionId(provider.getContext().getRegionId());
        try{

            if(json.has("name")){
                snapshot.setName(json.getString("name"));
                snapshot.setProviderSnapshotId(json.getString("name"));
            }
            if(json.has("description")){
                snapshot.setDescription(json.getString("description"));
            }
            if(json.has("sourceDisk")){
                snapshot.setVolumeId(json.getString("sourceDisk"));
            }
            if(json.has("status")){
                String status = json.getString("status");
                SnapshotState state;
                if(status.equals("CREATING")){
                    state = SnapshotState.PENDING;
                }else if(status.equals("READY")){
                    state = SnapshotState.AVAILABLE;
                }else{
                    state = SnapshotState.DELETED;
                }
                snapshot.setCurrentState(state);
            }

            if(json.has("diskSizeGb")){
                int size = Integer.parseInt(json.getString("diskSizeGb"));
                snapshot.setSizeInGb(size);
            }else snapshot.setSizeInGb(0);

            if(snapshot.getDescription() == null){
                snapshot.setDescription(snapshot.getName() + " [" + snapshot.getSizeInGb() + " GB]");
            }
            if(snapshot.getSizeInGb() < 1){

                GoogleCompute svc = provider.getComputeServices();

                if(svc != null){
                    VolumeSupport vs = svc.getVolumeSupport();
                    try{
                        Volume vol = vs.getVolume(snapshot.getVolumeId());

                        if(vol != null){
                            snapshot.setSizeInGb(vol.getSizeInGigabytes());
                        }
                    }catch(InternalException ignore){
                        // ignore
                    }
                }
            }
        }catch(JSONException e){
            logger.error("Failed to parse JSON from the cloud: " + e.getMessage());
            e.printStackTrace();
            throw new CloudException(e);
        }
        return snapshot;
    }

    @Override
    public Requirement identifyAttachmentRequirement() throws InternalException, CloudException{
        return Requirement.OPTIONAL;
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
        List<ResourceStatus> status = new ArrayList<ResourceStatus>();

        Iterable<Snapshot> snapshots = listSnapshots();
        for(Snapshot snapshot : snapshots){
            SnapshotState state = snapshot.getCurrentState();
            ResourceStatus resStatus = new ResourceStatus(snapshot.getProviderSnapshotId(), state);
            status.add(resStatus);
        }
        return status;
    }

    @Override
    public @Nonnull Iterable<Snapshot> listSnapshots() throws InternalException, CloudException{
        GoogleMethod method = new GoogleMethod(provider);

        JSONArray list = method.get(GoogleMethod.SNAPSHOT);

        if(list == null){
            return Collections.emptyList();
        }
        ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();
        for(int i = 0; i < list.length(); i++){
            try{
                Snapshot snap = toSnapshot(list.getJSONObject(i));

                if(snap != null){
                    snapshots.add(snap);
                }
            }catch(JSONException e){
                logger.error("Failed to parse JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(e);
            }
        }
        return snapshots;
    }

    @Override
    public @Nonnull Iterable<Snapshot> listSnapshots(SnapshotFilterOptions options) throws InternalException, CloudException{
        GoogleMethod method = new GoogleMethod(provider);

        Param param = new Param("filter", options.getRegex());
        JSONArray list = method.get(GoogleMethod.SNAPSHOT, param);

        if(list == null){
            return Collections.emptyList();
        }
        ArrayList<Snapshot> snapshots = new ArrayList<Snapshot>();
        for(int i = 0; i < list.length(); i++){
            try{
                Snapshot snap = toSnapshot(list.getJSONObject(i));

                if(snap != null){
                    snapshots.add(snap);
                }
            }catch(JSONException e){
                logger.error("Failed to parse JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(e);
            }
        }
        return snapshots;
    }

    @Override
    public void remove(@Nonnull String snapshotId) throws InternalException, CloudException{
        GoogleMethod method = new GoogleMethod(provider);
        method.delete(GoogleMethod.SNAPSHOT, new GoogleMethod.Param("id", snapshotId));

        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 15L);

        while(timeout > System.currentTimeMillis()){
            Snapshot snap = getSnapshot(snapshotId);

            if(snap == null || snap.getCurrentState().equals(SnapshotState.DELETED)){
                return;
            }
            try{
                Thread.sleep(15000L);
            }catch(InterruptedException ignore){
            }
        }
        throw new CloudException("Snapshot deletion failed !");

    }

    @Override
    public void removeAllSnapshotShares(@Nonnull String providerSnapshotId) throws CloudException, InternalException{
        // NO OP
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
    public @Nonnull Iterable<Snapshot> searchSnapshots(@Nonnull SnapshotFilterOptions arg0) throws InternalException, CloudException{
        List<Snapshot> searchSnapshots = new ArrayList<Snapshot>();

        Iterable<Snapshot> snapshots = listSnapshots();
        Map<String, String> tag = arg0.getTags();

        for(Snapshot snapshot : snapshots){
            for(Entry<String, String> entry : tag.entrySet()){
                String keyword = entry.getValue();
                if(keyword.equals(snapshot.getProviderSnapshotId()) || !snapshot.getName().contains(keyword) || !snapshot.getDescription().contains(keyword)){
                    searchSnapshots.add(snapshot);
                    break;
                }
            }
        }
        return searchSnapshots;
    }

    @Override
    public @Nonnull Iterable<Snapshot> searchSnapshots(String ownerId, String keyword) throws InternalException, CloudException{
        List<Snapshot> searchSnapshots = new ArrayList<Snapshot>();

        Iterable<Snapshot> snapshots = listSnapshots();
        for(Snapshot snapshot : snapshots){
            if(ownerId != null && !ownerId.equals(snapshot.getProviderSnapshotId())){
                continue;
            }
            if(keyword != null && !snapshot.getName().contains(keyword) && !snapshot.getDescription().contains(keyword)){
                continue;
            }
            searchSnapshots.add(snapshot);
        }
        return searchSnapshots;
    }

    @Override
    public boolean supportsSnapshotCopying() throws CloudException, InternalException{
        return false;
    }

    @Override
    public boolean supportsSnapshotCreation() throws CloudException, InternalException{
        return true;
    }

    @Override
    public boolean supportsSnapshotSharing() throws InternalException, CloudException{
        return false;
    }

    @Override
    public boolean supportsSnapshotSharingWithPublic() throws InternalException, CloudException{
        return false;
    }

    @Override
    public void updateTags(@Nonnull String snapshotId, @Nonnull Tag... tags) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google snapshot does not contain meta data");
    }

    @Override
    public void updateTags(@Nonnull String[] snapshotIds, @Nonnull Tag... tags) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Google snapshot does not contain meta data");
    }

}
