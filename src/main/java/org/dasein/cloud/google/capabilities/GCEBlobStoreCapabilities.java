package org.dasein.cloud.google.capabilities;

import java.util.Locale;

import javax.annotation.Nonnull;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.Cloud;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.storage.BlobStoreCapabilities;
import org.dasein.cloud.util.NamingConstraints;
import org.dasein.util.uom.storage.Byte;
import org.dasein.util.uom.storage.Storage;

public class GCEBlobStoreCapabilities extends AbstractCapabilities<Google> implements BlobStoreCapabilities {
    private Google cloud;

    public GCEBlobStoreCapabilities( @Nonnull Google cloud ) {
        super(cloud);
        this.cloud = cloud;
    }

    @Override
    public String getAccountNumber() {
        return cloud.getContext().getAccountNumber();
    }

    @Override
    public String getRegionId() {
        return cloud.getContext().getRegionId();
    }

    @Override
    public boolean allowsNestedBuckets() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean allowsRootObjects() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean allowsPublicSharing() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int getMaxBuckets() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Storage<Byte> getMaxObjectSize() throws InternalException, CloudException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getMaxObjectsPerBucket() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public NamingConstraints getBucketNamingConstraints() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public NamingConstraints getObjectNamingConstraints() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getProviderTermForBucket(Locale locale) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getProviderTermForObject(Locale locale) {
        // TODO Auto-generated method stub
        return null;
    }
}
