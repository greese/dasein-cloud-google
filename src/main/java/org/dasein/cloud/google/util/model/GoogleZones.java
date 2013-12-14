package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.Zone;
import org.dasein.cloud.dc.DataCenter;

/**
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleZones {

	public static DataCenter toDaseinDataCenter(Zone googleDataCenter) {
		DataCenter dataCenter = new DataCenter();

		dataCenter.setActive(true);
		dataCenter.setAvailable(true);
		dataCenter.setName(googleDataCenter.getName());
		dataCenter.setProviderDataCenterId(googleDataCenter.getName());
		dataCenter.setRegionId(googleDataCenter.getRegion());

		return dataCenter;
	}

}
