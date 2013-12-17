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

package org.dasein.cloud.google;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.RegionList;
import com.google.api.services.compute.model.Zone;
import com.google.api.services.compute.model.ZoneList;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.google.util.model.GoogleRegions;
import org.dasein.cloud.google.util.model.GoogleZones;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

import static org.dasein.cloud.google.util.ExceptionUtils.handleGoogleResponseError;

/**
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleDataCenters implements DataCenterServices {

	private Google provider;

	GoogleDataCenters(@Nonnull Google provider) {
		this.provider = provider;
	}

	@Override
	@Nullable
	public DataCenter getDataCenter(@Nonnull String dataCenterId) throws InternalException, CloudException {
		APITrace.begin(provider, "getDataCenter");

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Zones.Get getZoneRequest = compute.zones().get(provider.getContext().getAccountNumber(), dataCenterId);
			Zone zone = getZoneRequest.execute();
			return zone != null ? GoogleZones.toDaseinDataCenter(zone) : null;
		} catch (IOException e) {
			handleGoogleResponseError(e);
		} finally {
			APITrace.end();
		}

		return null;
	}

	@Override
	@Nonnull
	public String getProviderTermForDataCenter(@Nonnull Locale locale) {
		return "availability group";
	}

	@Override
	@Nonnull
	public String getProviderTermForRegion(@Nonnull Locale locale) {
		return "region";
	}

	@Override
	@Nullable
	public Region getRegion(@Nonnull String providerRegionId) throws InternalException, CloudException {
		APITrace.begin(provider, "getRegion");

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Regions.Get getRegionsRequest = compute.regions().get(provider.getContext().getAccountNumber(), providerRegionId);
			com.google.api.services.compute.model.Region region = getRegionsRequest.execute();
			return region != null ? GoogleRegions.toDaseinRegion(region) : null;
		} catch (IOException e) {
			handleGoogleResponseError(e);
		} finally {
			APITrace.end();
		}

		return null;
	}

	@Override
	@Nonnull
	public Collection<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
		APITrace.begin(provider, "listDataCenters");

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();
		ProviderContext context = provider.getContext();

		// load from cache if possible
		Cache<DataCenter> cache = Cache.getInstance(provider, context.getAccountNumber() + "-" + providerRegionId + "-datacenters",
				DataCenter.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(1, TimePeriod.HOUR));
		Collection<DataCenter> dataCenters = (Collection<DataCenter>) cache.get(context);
		if (dataCenters != null) {
			return dataCenters;
		}

		try {
			Compute.Zones.List listZonesRequest = compute.zones().list(provider.getContext().getAccountNumber());
			listZonesRequest.setFilter("region eq .*" + providerRegionId);

			ZoneList zoneList = listZonesRequest.execute();
			if (zoneList.getItems() == null) {
				return Collections.emptyList();
			}

			dataCenters = new ArrayList<DataCenter>(zoneList.getItems().size());
			for (Zone dataCenter : zoneList.getItems()) {
				dataCenters.add(GoogleZones.toDaseinDataCenter(dataCenter));
			}
			// cache result for 1 hour
			cache.put(context, dataCenters);

			return dataCenters;

		} catch (IOException e) {
			handleGoogleResponseError(e);
		} finally {
			APITrace.end();
		}

		throw new IllegalStateException();
	}

	@Override
	public Collection<Region> listRegions() throws InternalException, CloudException {
		APITrace.begin(provider, "listRegions");

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		ProviderContext context = provider.getContext();
		Compute compute = provider.getGoogleCompute();

		// load from cache if possible
		Cache<Region> cache = Cache.getInstance(provider, "regions", Region.class, CacheLevel.CLOUD_ACCOUNT,
				new TimePeriod<Hour>(10, TimePeriod.HOUR));
		Collection<Region> cachedRegions = (Collection<Region>) cache.get(context);
		if (cachedRegions != null) {
			return cachedRegions;
		}

		try {
			Compute.Regions.List listRegionsRequest = compute.regions().list(provider.getContext().getAccountNumber());
			RegionList regionList = listRegionsRequest.execute();

			List<Region> regions = new ArrayList<Region>(regionList.getItems().size());
			for (com.google.api.services.compute.model.Region region : regionList.getItems()) {
				regions.add(GoogleRegions.toDaseinRegion(region));
			}
			// cache result for 10 hours
			cache.put(context, regions);

			return regions;
		} catch (IOException e) {
			handleGoogleResponseError(e);
		} finally {
			APITrace.end();
		}

		throw new IllegalStateException();
	}

}
