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
import java.util.*;

import javax.annotation.Nonnull;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Snapshots.Get;
import com.google.api.services.compute.model.*;
import com.google.api.services.compute.model.Snapshot;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEVolumeCapabilities;
import org.dasein.cloud.util.APITrace;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.apache.log4j.Logger;
/**
 * Implements the volume services supported in the Google API.
 * @author Drew Lyall
 * @version 2014.03 initial version
 * @since 2014.03
 */
public class DiskSupport extends AbstractVolumeSupport {
	static private final Logger logger = Google.getLogger(DiskSupport.class);

	private Google provider;

	public DiskSupport(Google provider) {
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
                Volume volume = getVolume(volumeId);

                AttachedDisk attachedDisk = new AttachedDisk();
                attachedDisk.setSource(volume.getTag("contentLink"));
                attachedDisk.setType("PERSISTENT");
                attachedDisk.setMode("READ_WRITE");
                attachedDisk.setBoot(false);
                attachedDisk.setDeviceName(deviceId);

                Operation job = gce.instances().attachDisk(provider.getContext().getAccountNumber(), vm.getProviderDataCenterId(), toServer, attachedDisk).execute();

                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", vm.getProviderDataCenterId())){
                    throw new CloudException("An error occurred attaching the disk: Operation Timedout");
                }
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
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
                disk.setZone(options.getDataCenterId());
                if (options.getSnapshotId() != null) {
                    //disk.setSourceSnapshotId(options.getSnapshotId()); // <- does not work
                    Snapshot snapshot = gce.snapshots().get(provider.getContext().getAccountNumber(), options.getSnapshotId()).execute();
                    disk.setSourceSnapshot(snapshot.getSelfLink());
                }
                Operation job = gce.disks().insert(provider.getContext().getAccountNumber(), options.getDataCenterId(), disk).execute();

                GoogleMethod method = new GoogleMethod(provider);
                return method.getOperationTarget(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", options.getDataCenterId(), false);
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
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
                job = gce.instances().detachDisk(provider.getContext().getAccountNumber(), volume.getProviderDataCenterId(), volume.getProviderVirtualMachineId(), volume.getDeviceId()).execute();
                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.ZONE_OPERATION, "", volume.getProviderDataCenterId())){
                    throw new CloudException("An error occurred while detaching the volume: Operation Timedout");
                }
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException("An error occurred while detaching the volume: " + ex.getMessage());
			} catch (Exception ex) {
			    throw new CloudException(ex);
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
                return null;
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
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
            list.add("sdf");
            list.add("sdg");
            list.add("sdh");
            list.add("sdi");
            list.add("sdj");
            list.add("sdk");
            list.add("sdl");
            list.add("sdm");
            list.add("sdn");
            list.add("sdo");
            list.add("sdp");
            list.add("sdq");
            list.add("sdr");
            list.add("sds");
            list.add("sdt");
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
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
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
	        } catch (IOException ex) {
				logger.error(ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException("An error occurred while deleting the volume: " + ex.getMessage());
			}
        }
        finally {
            APITrace.end();
        }
    }

    public Volume toVolume(Disk disk) throws InternalException, CloudException{
        Volume volume = new Volume();
        volume.setProviderVolumeId(disk.getName());
        volume.setName(disk.getName());
        if(disk.getDescription() == null)volume.setDescription(disk.getName());
        else volume.setDescription(disk.getDescription());
        volume.setProviderRegionId(provider.getDataCenterServices().getRegionFromZone(disk.getZone().substring(disk.getZone().lastIndexOf("/") + 1)));

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = DateTime.parse(disk.getCreationTimestamp(), fmt);
        volume.setCreationTimestamp(dt.toDate().getTime());
        volume.setProviderDataCenterId(disk.getZone().substring(disk.getZone().lastIndexOf("/") + 1));
        volume.setCurrentState((disk.getStatus().equals("DONE") || disk.getStatus().equals("READY")) ? VolumeState.AVAILABLE : VolumeState.PENDING);
        volume.setType(VolumeType.HDD);
        volume.setFormat(VolumeFormat.BLOCK);
        volume.setSize(new Storage<Gigabyte>(disk.getSizeGb(), Storage.GIGABYTE));
        if(disk.getSourceSnapshotId() != null && !disk.getSourceSnapshotId().equals(""))volume.setProviderSnapshotId(disk.getSourceSnapshotId());
        volume.setTag("contentLink", disk.getSelfLink());

        //In order to list volumes with the attached VM, VMs must be listed. Doing it for now but, ick!
        Compute gce = provider.getGoogleCompute();
        try{
            //We only care about instances in the same zone as the disk
            InstanceList list = gce.instances().list(provider.getContext().getAccountNumber(), disk.getZone().substring(disk.getZone().lastIndexOf("/") + 1)).execute();
            if(list.getItems() != null){
                for(Instance instance : list.getItems()){
                    for(AttachedDisk attachedDisk : instance.getDisks()){
                        if(attachedDisk.getSource().equals(disk.getSelfLink())){
                            volume.setDeviceId(attachedDisk.getDeviceName());
                            volume.setProviderVirtualMachineId(instance.getName());
                            break;
                        }
                    }
                }
            }
        }
        catch(IOException ex){
            logger.error(ex.getMessage());
            return null;
        }
        return volume;
    }
}
