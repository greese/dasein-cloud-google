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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.DiskAggregatedList;
import com.google.api.services.compute.model.Operation;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleMethod.Param;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEVolumeCapabilities;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.util.APITrace;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.http.ParseException;
import org.apache.log4j.Logger;
/**
 * Implements the volume services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleDiskSupport extends AbstractVolumeSupport {
	static private final Logger logger = Google.getLogger(GoogleDiskSupport.class);

	private Google provider;

	public GoogleDiskSupport(Google provider) {
        super(provider);
        this.provider = provider;
    }

	@Override
	public void attach(@Nonnull String volumeId, @Nonnull String toServer, @Nonnull String deviceId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Volume.attach");
		try{
            Compute gce = provider.getGoogleCompute();

            try{
                VirtualMachine vm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(toServer);

                AttachedDisk attachedDisk = new AttachedDisk();
                attachedDisk.setType("PERSISTENT");
                attachedDisk.setMode("READ_WRITE");
                attachedDisk.setBoot(false);
                Operation job = gce.instances().attachDisk(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), toServer, attachedDisk).execute();

                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", vm.getProviderDataCenterId())){
                    throw new CloudException("An error occurred attaching the disk: Operation Timedout");
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred while attaching the disk: " + ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
	}

	@Override
	public @Nonnull String createVolume(@Nonnull VolumeCreateOptions options) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Volume.createVolume");
        try{
            Compute gce = provider.getGoogleCompute();

            try{
                Disk disk = new Disk();
                disk.setName(options.getName());
                disk.setSizeGb(options.getVolumeSize().longValue());
                Operation job = gce.disks().insert(provider.getContext().getAccountNumber(), options.getDataCenterId(), disk).execute();

                GoogleMethod method = new GoogleMethod(provider);
                return method.getOperationTarget(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", options.getDataCenterId(), false);
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred while creating the Volume: " + ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
	}

	@Override
	public void detach(@Nonnull String volumeId, boolean force) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Volume.detach");
        try{
            Volume volume = getVolume(volumeId);

            Compute gce = provider.getGoogleCompute();
            Operation job = null;
            try{
                job = gce.instances().detachDisk(provider.getContext().getAccountNumber(), volume.getProviderDataCenterId(), volume.getProviderVirtualMachineId(), volumeId).execute();
                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", volume.getProviderDataCenterId())){
                    throw new CloudException("An error occurred while detaching the volume: Operation Timedout");
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred while detaching the volume: " + ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
	}

    private transient volatile GCEVolumeCapabilities capabilities;
    @Override
    public @Nonnull GCEVolumeCapabilities getCapabilities() throws CloudException, InternalException{
        if(capabilities == null){
            capabilities = new GCEVolumeCapabilities(provider);
        }
        return capabilities;
    }

    @Override
	public int getMaximumVolumeCount() throws InternalException, CloudException {
		return 16;
	}

	@Override
	public Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException {
		return new Storage<Gigabyte>(10240, Storage.GIGABYTE);
	}

	@Override
	public @Nonnull Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException {
		return new Storage<Gigabyte>(200, Storage.GIGABYTE);
	}

	@Override
	public @Nonnull String getProviderTermForVolume(@Nonnull Locale locale) {
		return "disk";
	}

	@Override
	public Volume getVolume(@Nonnull String volumeId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Volume.getVolume");
        try{
            Compute gce = provider.getGoogleCompute();
            try{
                DiskAggregatedList diskList = gce.disks().aggregatedList(provider.getContext().getAccountNumber()).setFilter("name eq " + volumeId).execute();
                Iterator<String> zones = diskList.getItems().keySet().iterator();
                while(zones.hasNext()){
                    String zone = zones.next();
                    if(diskList.getItems().get(zone) != null && diskList.getItems().get(zone).getDisks() != null){
                        for(Disk disk : diskList.getItems().get(zone).getDisks()){
                            if(disk.getName().equals(volumeId))return toVolume(disk);
                        }
                    }
                }
                throw new CloudException("The volume: " + volumeId + " could not be found");
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred getting the volume: " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public @Nonnull Requirement getVolumeProductRequirement() throws InternalException, CloudException {
		return Requirement.NONE;
	}

	@Override
	public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException {
		return true;
	}

	@Override
	public @Nonnull Iterable<String> listPossibleDeviceIds(@Nonnull Platform platform) throws InternalException, CloudException {
		ArrayList<String> list = new ArrayList<String>();

		if( !platform.isWindows()) {
			list.add("/dev/sdf");
			list.add("/dev/sdg");
			list.add("/dev/sdh");
			list.add("/dev/sdi");
			list.add("/dev/sdj");
			list.add("/dev/sdk");
			list.add("/dev/sdl");
			list.add("/dev/sdm");
			list.add("/dev/sdn");
			list.add("/dev/sdo");
			list.add("/dev/sdp");
			list.add("/dev/sdq");
			list.add("/dev/sdr");
			list.add("/dev/sds");
			list.add("/dev/sdt");
		}
		return list;

	}

	@Override
	public @Nonnull Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException {
		return Collections.singletonList(VolumeFormat.BLOCK);
	}

	@Override
	public @Nonnull Iterable<VolumeProduct> listVolumeProducts() throws InternalException, CloudException {
        //TODO: Could implement something here - GCE doesn't charge for iops
		return Collections.emptyList();
	}

	@Override
	public @Nonnull Iterable<ResourceStatus> listVolumeStatus() throws InternalException, CloudException {
		List<ResourceStatus> status = new ArrayList<ResourceStatus>();

		Iterable<Volume> volumes = listVolumes();
		for (Volume volume: volumes) {
			VolumeState state = volume.getCurrentState();
			ResourceStatus resStatus = new ResourceStatus(volume.getProviderVolumeId(), state);
			status.add(resStatus);
		}
		return status;
	}

	@Override
	public @Nonnull Iterable<Volume> listVolumes() throws InternalException, CloudException {
		return listVolumes(null);
	}

	@Override
	public @Nonnull Iterable<Volume> listVolumes(VolumeFilterOptions options) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Volume.listVolumes");
        try{
            ArrayList<Volume> volumes = new ArrayList<Volume>();
            Compute gce = provider.getGoogleCompute();
            try{
                DiskAggregatedList diskList = gce.disks().aggregatedList(provider.getContext().getAccountNumber()).execute();
                Iterator<String> zones = diskList.getItems().keySet().iterator();
                while(zones.hasNext()){
                    String zone = zones.next();
                    if(diskList.getItems().get(zone) != null && diskList.getItems().get(zone).getDisks() != null){
                        for(Disk disk : diskList.getItems().get(zone).getDisks()){
                            Volume volume = toVolume(disk);
                            if( volume != null && (options == null || options.matches(volume)) ) {
                                volumes.add(volume);
                            }
                        }
                    }
                }
                return volumes;
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred listing Volumes: " + ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public void remove(@Nonnull String volumeId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Volume.remove");
        try{
            Compute gce = provider.getGoogleCompute();
            Volume volume = getVolume(volumeId);

            try{
                Operation job = gce.disks().delete(provider.getContext().getAccountNumber(), volume.getProviderDataCenterId(), volume.getProviderVolumeId()).execute();
                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", volume.getProviderDataCenterId())){
                    throw new CloudException("An error occurred while deleting the Volume: Operation Timedout");
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred while deleting the volume: " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    public Volume toVolume(Disk disk){
        Volume volume = new Volume();
        volume.setProviderProductId(disk.getName());
        volume.setName(disk.getName());
        volume.setDescription(disk.getDescription());
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse(disk.getCreationTimestamp(), fmt);
        volume.setCreationTimestamp(dt.toDate().getTime());
        volume.setProviderDataCenterId(disk.getZone());
        volume.setCurrentState(disk.getStatus().equals("DONE") ? VolumeState.AVAILABLE : VolumeState.PENDING);
        volume.setType(VolumeType.HDD);
        volume.setFormat(VolumeFormat.BLOCK);
        volume.setSize(new Storage<Gigabyte>(disk.getSizeGb(), Storage.GIGABYTE));
        if(disk.getSourceSnapshotId() != null && !disk.getSourceSnapshotId().equals(""))volume.setProviderSnapshotId(disk.getSourceSnapshotId());

        //TODO: Get vm disk is attached to

        volume.setTag("contentLink", disk.getSelfLink());

        return volume;
    }
}
