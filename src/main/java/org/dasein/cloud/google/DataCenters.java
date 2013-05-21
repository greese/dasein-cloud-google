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

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Day;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

/**
 * Unimplemented skeleton class
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class DataCenters implements DataCenterServices {
	static private final Logger logger = Google.getLogger(DataCenters.class);

	private Google provider;

	DataCenters(@Nonnull Google provider) { this.provider = provider; }


	String getRegionFromZone(@Nonnull String zone) {

		String region;

		int index = zone.lastIndexOf("-");
		region = zone.substring(0, index);

		return region;

	}

	@Override
	public @Nullable DataCenter getDataCenter(@Nonnull String dataCenterId) throws InternalException, CloudException {
		for( Region region : listRegions() ) {
			for( DataCenter dc : listDataCenters(region.getProviderRegionId()) ) {
				if( dataCenterId.equals(dc.getProviderDataCenterId()) ) {
					return dc;
				}
			}
		}
		return null;
	}

	@Override
	public @Nonnull String getProviderTermForDataCenter(@Nonnull Locale locale) {
		return "availability group";
	}

	@Override
	public @Nonnull String getProviderTermForRegion(@Nonnull Locale locale) {
		return "region";
	}

	@Override
	public @Nullable Region getRegion(@Nonnull String providerRegionId) throws InternalException, CloudException {
		for( Region r : listRegions() ) {
			if( providerRegionId.equals(r.getProviderRegionId()) ) {
				return r;
			}
		}
		return null;
	}

	@Override
	public @Nonnull Collection<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
		APITrace.begin(provider, "listDataCenters");
		try {
			Region region = getRegion(providerRegionId);

			if( region == null ) {
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
		}
		finally {
			APITrace.end();
		}
	}

	private @Nullable Region toRegion(@Nullable JSONObject r) throws CloudException, InternalException {
		if( r == null ) {
			return null;
		}

		Region region = new Region();

		region.setActive(true);
		region.setAvailable(true);
		region.setJurisdiction("US");

		try {
			if( r.has("name") ) {
				String zone = r.getString("name");
				String regionName = getRegionFromZone(zone);
				region.setProviderRegionId(regionName);
				region.setName(regionName);
			}
		}
		catch( JSONException e ) {
			logger.error("Failed to parse JSON from cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}
		if( region.getProviderRegionId() == null ) {
			return null;
		}
		if( region.getName() == null ) {
			region.setName(region.getProviderRegionId());
		}
		String n = region.getName();

		if( n.length() > 2 ) {
			region.setJurisdiction(n.substring(0, 2));
		}
		return region;
	}

	@Override
	public Collection<Region> listRegions() throws InternalException, CloudException {
		APITrace.begin(provider, "listRegions");
		try {
			ProviderContext ctx = provider.getContext();

			if( ctx == null ) {
				throw new NoContextException();
			}
			Cache<Region> cache = Cache.getInstance(provider, "regions", Region.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(10, TimePeriod.HOUR));
			Collection<Region> regions = (Collection<Region>)cache.get(ctx);

			if( regions != null ) {
				return regions;
			}
			regions = new ArrayList<Region>();

			GoogleMethod method = new GoogleMethod(provider);
			try {
				JSONArray list = method.get(GoogleMethod.ZONE);
				if( list != null )
					for( int i=0; i<list.length(); i++ ) {
						try {
							Region r = toRegion(list.getJSONObject(i));

							if( r != null && !regions.contains(r)) {
								regions.add(r);
							}
						}
						catch( JSONException e ) {
							logger.error("Failed to parse JSON: " + e.getMessage());
							e.printStackTrace();
							throw new CloudException(e);
						}
					}

			} catch (GoogleException e) {
				logger.error("Failed to listRegions : " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
			cache.put(ctx, regions);
			return regions;

		}
		finally {
			APITrace.end();
		}
	}
}
