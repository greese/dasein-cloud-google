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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.log4j.Logger;
/**
 * Implements the volume services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleDiskSupport implements VolumeSupport {
	static private final Logger logger = Google.getLogger(GoogleDiskSupport.class);

	private Google provider;

	public GoogleDiskSupport(Google provider) {	this.provider = provider; }

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public void attach(String volumeId, String toServer, String deviceId)
			throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support attaching volumes to an instance");
	}

	@Override
	public String create(String fromSnapshot, int sizeInGb, String inZone)
			throws InternalException, CloudException {
		if( fromSnapshot != null ) {
			return createVolume(VolumeCreateOptions.getInstanceForSnapshot(fromSnapshot, new Storage<Gigabyte>(sizeInGb, Storage.GIGABYTE), "dsn-auto-volume", "dsn-auto-volume").inDataCenter(inZone));
		}
		else {
			return createVolume(VolumeCreateOptions.getInstance(new Storage<Gigabyte>(sizeInGb, Storage.GIGABYTE), "dsn-auto-volume", "dsn-auto-volume").inDataCenter(inZone));
		}
	}

	@Nonnull
	@Override
	public Iterable<VmState> getAttachStates(@Nullable Volume volume) {
		logger.warn("Operation not supported yet");
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public Iterable<VmState> getDetachStates(@Nullable Volume volume) {
		logger.warn("Operation not supported yet");
		return Collections.emptyList();
	}

	@Override
	public @Nonnull String createVolume(VolumeCreateOptions options)
			throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();
		GoogleMethod method = new GoogleMethod(provider);
		if( ctx == null ) {
			throw new InternalException("No context was specified for this request");
		}
		JSONObject payload = new JSONObject();

		try {	

			payload.put("name", options.getName());
			if (options.getDescription() != null) payload.put("description", options.getDescription());
			if( options.getSnapshotId() != null ) {
				payload.put("sourceSnapshot", method.getEndpoint(ctx, GoogleMethod.SNAPSHOT) + "/" +options.getSnapshotId());
			} else payload.put("sizeGb", String.valueOf(options.getVolumeSize().getQuantity().intValue()));

			String zone = ctx.getRegionId() + "-a";
			if(options.getDataCenterId() != null) {
				zone = options.getDataCenterId();
			} 
			payload.put("zone", method.getEndpoint(ctx, GoogleMethod.ZONE) + "/" + zone);

		} catch (JSONException e) {
			e.printStackTrace();
			logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
			throw new CloudException(e);

		}

		JSONObject response = null;
		try {
			response = method.post(GoogleMethod.VOLUME, payload);
		} catch( GoogleException e ) {
			e.printStackTrace();
			logger.error(e.getLocalizedMessage());
			throw new CloudException(e);
		}

		String name = null;

		String status = method.getOperationStatus(GoogleMethod.OPERATION, response);
		if (status != null && status.equals("DONE")) {
			if( response.has("targetLink") ) {
				try {
					name = response.getString("targetLink");
				} catch (JSONException e) {
					e.printStackTrace();
					logger.error(e.getLocalizedMessage());
					throw new CloudException(e);
				}
				return GoogleMethod.getResourceName(name, GoogleMethod.VOLUME);
			}
		}
		throw new CloudException("create volume operation failed");
	}

	@Override
	public void detach(String volumeId) throws InternalException,
	CloudException {
		throw new OperationNotSupportedException("Google does not support detach volumes from a running instance");
	}

	@Override
	public void detach(String volumeId, boolean force)
			throws InternalException, CloudException {
		throw new OperationNotSupportedException("Google does not support detach volumes from a running instance");
	}

	@Override
	public int getMaximumVolumeCount() throws InternalException, CloudException {
		return -2;
	}

	@Override
	public Storage<Gigabyte> getMaximumVolumeSize() throws InternalException,
	CloudException {
		// Setting the size of a persistent disk
		return new Storage<Gigabyte>(1024, Storage.GIGABYTE);
	}

	@Override
	public Storage<Gigabyte> getMinimumVolumeSize() throws InternalException,
	CloudException {
		// TODO: Need to check what is the minimum volume size supported by GCE
		return new Storage<Gigabyte>(10, Storage.GIGABYTE);
	}

	@Override
	public String getProviderTermForVolume(Locale locale) {
		return "disk";
	}

	@Override
	public Volume getVolume(String volumeId) throws InternalException,
	CloudException {

		GoogleMethod method = new GoogleMethod(provider);
		JSONArray list = method.get(GoogleMethod.VOLUME  + "/" + volumeId);

		if( list == null ) {
			return null;
		}

		for( int i=0; i<list.length(); i++ ) {
			try {
				Volume vol = toVolume(list.getJSONObject(i));

				if( vol != null && vol.getProviderVolumeId().equals(volumeId) ) {
					return vol; 
				}
			}
			catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}
		return null;
	}

	private @Nullable Volume toVolume(JSONObject json) throws CloudException, JSONException {
		if( json == null ) {
			return null;
		} 
		Volume vol = new Volume();

		vol.setProviderRegionId(provider.getContext().getRegionId());
		vol.setType(VolumeType.HDD); 

		if( json.has("name") ) {
			vol.setProviderVolumeId(json.getString("name"));
			vol.setName(json.getString("name"));
		}

		if( json.has("description") ) {
			vol.setDescription(json.getString("description"));
		}
		if( json.has("sizeGb") ) {
			int size = Integer.parseInt(json.getString("sizeGb"));
			vol.setSize(new Storage<Gigabyte>(size, Storage.GIGABYTE));
		}
		if( json.has("sourceSnapshot") ) {
			vol.setProviderSnapshotId(GoogleMethod.getResourceName(json.getString("sourceSnapshot"), GoogleMethod.SNAPSHOT));
		}
		if( json.has("zone") ) {
			vol.setProviderDataCenterId(GoogleMethod.getResourceName(json.getString("zone") , GoogleMethod.ZONE));
		}

		if(json.has("creationTimestamp") ) {
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
			String value = json.getString("creationTimestamp");
			try {
				vol.setCreationTimestamp(fmt.parse(value).getTime());
			} catch (java.text.ParseException e) {
				logger.error(e);
				e.printStackTrace();
				throw new CloudException(e);
			}				
		}

		if( json.has("status") ) {
			String s = json.getString("status");
			VolumeState state;

			if( s.equals("CREATING")) {
				state = VolumeState.PENDING;
			}
			else if( s.equals("READY")) {
				state = VolumeState.AVAILABLE;
			}
			else {
				state = VolumeState.DELETED;
			}
			vol.setCurrentState(state);
		}

		try {
			Iterable<String> vmIds = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachineWithVolume(vol.getProviderVolumeId());
			if (vmIds != null) vol.setProviderVirtualMachineId(vmIds.iterator().next());
		} catch (InternalException e) {
			e.printStackTrace();
			logger.error("Setting virutal machine id for disk failed");
			throw new CloudException(e);
		}

		return vol;
	}

	@Override
	public Requirement getVolumeProductRequirement() throws InternalException,
	CloudException {
		return Requirement.NONE;
	}

	@Override
	public boolean isVolumeSizeDeterminedByProduct() throws InternalException,
	CloudException {
		return true;
	}

	@Override
	public Iterable<String> listPossibleDeviceIds(Platform platform)
			throws InternalException, CloudException {
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
	public Iterable<VolumeFormat> listSupportedFormats()
			throws InternalException, CloudException {
		return Collections.singletonList(VolumeFormat.BLOCK);
	}

	@Override
	public Iterable<VolumeProduct> listVolumeProducts()
			throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<ResourceStatus> listVolumeStatus()
			throws InternalException, CloudException {
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
	public Iterable<Volume> listVolumes() throws InternalException, CloudException {
    return listVolumes( VolumeFilterOptions.getInstance() );
	}

	@Override
	public Iterable<Volume> listVolumes(VolumeFilterOptions options)
			throws InternalException, CloudException {

		GoogleMethod method = new GoogleMethod(provider);

    Param param = new Param("filter", options.getRegex());
    JSONArray list = method.get( GoogleMethod.VOLUME, param );

		ArrayList<Volume> volumes = new ArrayList<Volume>();

		if (list != null)
			for( int i=0; i<list.length(); i++ ) {
				try {
					Volume vm = toVolume(list.getJSONObject(i));

					if( vm != null ) {
						volumes.add(vm);
					}
				}
				catch( JSONException e ) {
					logger.error("Failed to parse JSON: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}
			}

		return volumes;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public void remove(String volumeId) throws InternalException,
	CloudException {
		GoogleMethod method = new GoogleMethod(provider);
		method.delete(GoogleMethod.VOLUME, new GoogleMethod.Param("id", volumeId));
		long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 15L);

		while( timeout > System.currentTimeMillis() ) {
			Volume vol = getVolume(volumeId);

			if( vol == null || vol.getCurrentState().equals(VolumeState.DELETED) ) {
				return;
			}
			try { Thread.sleep(15000L); }
			catch( InterruptedException ignore ) { }
		}
		throw new CloudException("Volume deletion failed !");
	}

	@Override
	public void removeTags(String volumeId, Tag... tags) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("Google volume does not contain meta data");
	}

	@Override
	public void removeTags(String[] volumeIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain meta data");
	}

	@Override
	public void updateTags(String volumeId, Tag... tags) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("Google volume does not contain meta data");
	}

	@Override
	public void updateTags(String[] volumeIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google volume does not contain meta data");
	}

}
