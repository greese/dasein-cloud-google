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
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.google.compute.util.DasinModelConverter;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Unimplemented skeleton class
 *
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class DataCenters implements DataCenterServices {

	private static final Logger logger = Google.getLogger(DataCenters.class);

	private Google provider;

	DataCenters(@Nonnull Google provider) {
		this.provider = provider;
	}

	@Override
	@Nullable
	public DataCenter getDataCenter(@Nonnull String dataCenterId) throws InternalException, CloudException {
		for (Region region : listRegions()) {
			for (DataCenter dc : listDataCenters(region.getProviderRegionId())) {
				if (dataCenterId.equals(dc.getProviderDataCenterId())) {
					return dc;
				}
			}
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
		for (Region region : listRegions()) {
			if (providerRegionId.equals(region.getProviderRegionId())) {
				return region;
			}
		}
		return null;
	}

	@Override
	@Nonnull
	public Collection<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
		APITrace.begin(provider, "listDataCenters");
		try {
			Region region = getRegion(providerRegionId);
			if (region == null) {
				throw new CloudException("No such region: " + providerRegionId);
			}

			DataCenter dc = new DataCenter();
			dc.setActive(true);
			dc.setAvailable(true);
			dc.setName(region.getName() + "-a");
			dc.setProviderDataCenterId(region.getProviderRegionId() + "-a");
			dc.setRegionId(providerRegionId);

			return Collections.singletonList(dc);
		} catch (GoogleException e) {
			logger.error("Failed to listDatacenters : " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		} finally {
			APITrace.end();
		}
	}

	@Override
	public Collection<Region> listRegions() throws InternalException, CloudException {
		APITrace.begin(provider, "listRegions");

		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		ProviderContext ctx = provider.getContext();
		Compute compute = provider.getGoogleCompute();

		try {
			Cache<Region> cache = Cache.getInstance(provider, "regions", Region.class,
					CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(10, TimePeriod.HOUR));
			Collection<Region> regions = (Collection<Region>) cache.get(ctx);
			if (regions != null && !regions.isEmpty()) {
				return regions;
			}
			regions = new ArrayList<Region>();

			Compute.Regions.List regionsListRequest = compute.regions().list(Google.GRID_PROJECT_ID);
			RegionList regionList = regionsListRequest.execute();
			for (com.google.api.services.compute.model.Region region : regionList.getItems()) {
				regions.add(DasinModelConverter.from(region));
			}

			cache.put(ctx, regions);

			return regions;
		} catch (IOException e) {
			throw new CloudException(e);
		} finally {
			APITrace.end();
		}
	}

}
