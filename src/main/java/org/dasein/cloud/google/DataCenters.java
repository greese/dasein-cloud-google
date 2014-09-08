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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterCapabilities;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.dc.ResourcePool;
import org.dasein.cloud.dc.StoragePool;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Zone;

/**
 * Implementation of GCE Regions and Zones
 * @author Drew Lyall
 * @version 2014.03 initial version
 * @since 2014.03
 */
public class DataCenters implements DataCenterServices {
	static private final Logger logger = Google.getLogger(DataCenters.class);

    private static HashMap<String, String> zone2Region = new HashMap<String, String>();

	private Google provider;

	DataCenters(@Nonnull Google provider) {
        this.provider = provider;
    }

    private transient volatile GoogleDataCenterCapabilities capabilities;
    @Nonnull
    @Override
    public DataCenterCapabilities getCapabilities() throws InternalException, CloudException {
        if( capabilities == null ) {
            capabilities = new GoogleDataCenterCapabilities(provider);
        }
        return capabilities;
    }

    @Override
	public @Nullable DataCenter getDataCenter(@Nonnull String dataCenterId) throws InternalException, CloudException {
        Compute gce = provider.getGoogleCompute();
        try{
            Zone dataCenter = gce.zones().get(provider.getContext().getAccountNumber(), dataCenterId).execute();
            return toDataCenter(dataCenter);
	    } catch (IOException ex) {
			logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
	            throw new CloudException("An error occurred retrieving the dataCenter: " + dataCenterId + ": " + ex.getMessage());
		}
	}

	@Override
	public @Nonnull String getProviderTermForDataCenter(@Nonnull Locale locale) {
		return "zone";
	}

	@Override
	public @Nonnull String getProviderTermForRegion(@Nonnull Locale locale) {
		return "region";
	}

	@Override
	public @Nullable Region getRegion(@Nonnull String providerRegionId) throws InternalException, CloudException {
        Compute gce = provider.getGoogleCompute();
        try{
            com.google.api.services.compute.model.Region r = gce.regions().get(provider.getContext().getAccountNumber(), providerRegionId).execute();
            return toRegion(r);
	    } catch (IOException ex) {
			logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException("An error occurred retrieving the region: " + providerRegionId + ": " + ex.getMessage());
		}
	}

	@Override
	public @Nonnull Collection<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
		APITrace.begin(provider, "listDataCenters");
		try {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                throw new NoContextException();
            }

            Collection<DataCenter> dataCenters;
            Cache<DataCenter> cache = null;
            String originalRegionId = ctx.getRegionId();

            if(providerRegionId.equals(originalRegionId)) {
                cache = Cache.getInstance(provider, "datacenters", DataCenter.class, CacheLevel.REGION_ACCOUNT, new TimePeriod<Hour>(10, TimePeriod.HOUR));
                dataCenters = (Collection<DataCenter>)cache.get(ctx);
                if(dataCenters != null){
                    return dataCenters;
                }
            }
            dataCenters = new ArrayList<DataCenter>();

            Compute gce = provider.getGoogleCompute();
            Compute.Zones.List gceDataCenters = null;
            try{
                gceDataCenters = gce.zones().list(ctx.getAccountNumber());
                List<Zone> dataCenterList = gceDataCenters.execute().getItems();
                for(int i=0;i<dataCenterList.size();i++){
                    Zone current = dataCenterList.get(i);

                    String region = current.getRegion().substring(current.getRegion().lastIndexOf("/") + 1);
                    if (region.equals(providerRegionId)) {
                        dataCenters.add(toDataCenter(current));
                    }

                    zone2Region.put(current.getName(), region);
                }
    	    } catch (IOException ex) {
    	    	logger.error("Failed to listDataCenters: " + ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
    				throw new CloudException(CloudErrorType.COMMUNICATION, gceDataCenters.getLastStatusCode(), gceDataCenters.getLastStatusMessage(), "An error occurred while listing DataCenters");
    		}
            if (cache != null) {
                cache.put(ctx, dataCenters);
            }
            return dataCenters;
        }
		finally {
			APITrace.end();
		}
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

            Compute gce = provider.getGoogleCompute();
            Compute.Regions.List gceRegions = null;
            try{
                gceRegions = gce.regions().list(ctx.getAccountNumber());
                List<com.google.api.services.compute.model.Region> regionList = gceRegions.execute().getItems();
                for(int i=0;i<regionList.size();i++){
                    com.google.api.services.compute.model.Region current = regionList.get(i);
                    regions.add(toRegion(current));
                }
    	    } catch (IOException ex) {
    	    	logger.error("Failed to listRegions: " + ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
    				throw new CloudException(CloudErrorType.COMMUNICATION, gceRegions.getLastStatusCode(), gceRegions.getLastStatusMessage(), "An error occurred while listing regions");
    		}
            cache.put(ctx, regions);
            return regions;
		}
		finally {
			APITrace.end();
		}
	}

    public @Nonnull String getRegionFromZone(@Nonnull String zoneName) throws CloudException, InternalException{
        if(zoneName.contains("zones/"))zoneName = zoneName.replace("zones/", "");
        if(zone2Region == null || !zone2Region.containsKey(zoneName)){
            for(Region r : listRegions()){
                listDataCenters(r.getProviderRegionId());
            }
        }
        return zone2Region.get(zoneName);
    }

    private Region toRegion(com.google.api.services.compute.model.Region googleRegion){
        Region region = new Region();
        region.setName(googleRegion.getName());
        //region.setProviderRegionId(googleRegion.getId() + ""); - GCE uses name as IDs
        region.setProviderRegionId(googleRegion.getName());
        region.setActive(true);
        region.setAvailable(true);
        if( googleRegion.getName().startsWith("eu") ) {
            region.setJurisdiction("EU");
        }
        else region.setJurisdiction("US");
        return region;
    }

    private DataCenter toDataCenter(Zone zone){
        DataCenter dc = new DataCenter();
        dc.setActive(true);
        dc.setAvailable(false);
        //dc.setProviderDataCenterId(zone.getId() + ""); - GCE uses name as IDs
        dc.setProviderDataCenterId(zone.getName());
        dc.setRegionId(zone.getRegion().substring(zone.getRegion().lastIndexOf("/") + 1));
        dc.setName(zone.getName());
        if(zone.getStatus().equals("UP"))dc.setAvailable(true);

        return dc;
    }

    @Override
    public Collection<ResourcePool> listResourcePools(String providerDataCenterId) throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Override
    public ResourcePool getResourcePool(String providerResourcePoolId) throws InternalException, CloudException {
        return null;
    }

    @Override
    public Collection<StoragePool> listStoragePools() throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public StoragePool getStoragePool(String providerStoragePoolId) throws InternalException, CloudException {
        return null;
    }

    @Nonnull
    @Override
    public Collection<Folder> listVMFolders() throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Folder getVMFolder(String providerVMFolderId) throws InternalException, CloudException {
        return null;
    }
}
