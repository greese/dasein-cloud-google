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
import org.dasein.util.uom.storage.Gigabyte;
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
        return true;
    }

    @Override
    public boolean allowsRootObjects() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsPublicSharing() throws CloudException, InternalException {
        return true;
    }

    @Override
    public int getMaxBuckets() throws CloudException, InternalException {
        return Integer.MAX_VALUE -1;
    }

    @Override
    public Storage<Byte> getMaxObjectSize() throws InternalException, CloudException {
        return (Storage<org.dasein.util.uom.storage.Byte>)new Storage<Gigabyte>(4L, Storage.GIGABYTE).convertTo(Storage.BYTE);
    }

    @Override
    public int getMaxObjectsPerBucket() throws CloudException, InternalException {
        return Integer.MAX_VALUE -1;
    }

    @Override
    public NamingConstraints getBucketNamingConstraints() throws CloudException, InternalException {
        return NamingConstraints.getAlphaNumeric(3, 63)
                .withRegularExpression("^[a-z][_.-a-z0-9]{0,61}[a-z0-9]$")
                .lowerCaseOnly()
                .withNoSpaces()
                .withLastCharacterSymbolAllowed(false)
                .constrainedBy('-', '_', '.');
    }

    @Override
    public NamingConstraints getObjectNamingConstraints() throws CloudException, InternalException {
        return NamingConstraints.getStrictInstance(1, 255);
    }

    @Override
    public String getProviderTermForBucket(Locale locale) {
        return "Buckets";
    }

    @Override
    public String getProviderTermForObject(Locale locale) {
        return "Objects";
    }
}
