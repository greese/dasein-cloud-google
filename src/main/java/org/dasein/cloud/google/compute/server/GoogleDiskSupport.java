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
import org.dasein.cloud.util.APITrace;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
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

        }
        finally{
            APITrace.end();
        }
	}

	@Override
	public @Nonnull String createVolume(@Nonnull VolumeCreateOptions options) throws InternalException, CloudException {
		//TODO Implement me
        return "";
	}

	@Override
	public void detach(@Nonnull String volumeId, boolean force) throws InternalException, CloudException {
        //TODO: Implement me
	}

	@Override
	public int getMaximumVolumeCount() throws InternalException, CloudException {
		return -2;
	}

	@Override
	public Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException {
		// Setting the size of a persistent disk
        //TODO: Implement me
		return new Storage<Gigabyte>(1024, Storage.GIGABYTE);
	}

	@Override
	public @Nonnull Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException {
		// TODO: Need to check what is the minimum volume size supported by GCE
		return new Storage<Gigabyte>(10, Storage.GIGABYTE);
	}

	@Override
	public String getProviderTermForVolume(Locale locale) {
		return "disk";
	}

	@Override
	public Volume getVolume(String volumeId) throws InternalException, CloudException {
		//TODO: Implement me
		return null;
	}

	@Override
	public Requirement getVolumeProductRequirement() throws InternalException, CloudException {
		return Requirement.NONE;
	}

	@Override
	public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException {
		return true;
	}

	@Override
	public Iterable<String> listPossibleDeviceIds(Platform platform) throws InternalException, CloudException {
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
	public Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException {
		return Collections.singletonList(VolumeFormat.BLOCK);
	}

	@Override
	public Iterable<VolumeProduct> listVolumeProducts() throws InternalException, CloudException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<ResourceStatus> listVolumeStatus() throws InternalException, CloudException {
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
		//TODO: Implement me
        return null;
	}

	@Override
	public Iterable<Volume> listVolumes(VolumeFilterOptions options) throws InternalException, CloudException {
        //TODO: Implement me
        return null;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public void remove(String volumeId) throws InternalException, CloudException {
        //TODO: Implement me
	}
}
