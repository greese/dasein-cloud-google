package org.dasein.cloud.google;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.dc.DataCenterCapabilities;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * User: daniellemayne
 * Date: 04/07/2014
 * Time: 16:30
 */
public class GoogleDataCenterCapabilities extends AbstractCapabilities<Google> implements DataCenterCapabilities {
    public GoogleDataCenterCapabilities(@Nonnull Google provider) {
        super(provider);
    }
    @Override
    public String getProviderTermForDataCenter(Locale locale) {
        return "zone";
    }

    @Override
    public String getProviderTermForRegion(Locale locale) {
        return "region";
    }

    @Override
    public boolean supportsResourcePools() {
        return false;
    }

    @Override
    public boolean supportsStoragePools() {
        return false;
    }

    @Override
    public boolean supportsAffinityGroups() {
        // TODO Auto-generated method stub
        return false;
    }
}
