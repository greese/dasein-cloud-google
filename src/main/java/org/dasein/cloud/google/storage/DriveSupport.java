/**
 * Copyright (C) 2009-2014 Dell, Inc.
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

package org.dasein.cloud.google.storage;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.storage.AbstractBlobStoreSupport;
import org.dasein.cloud.storage.Blob;
import org.dasein.cloud.storage.FileTransfer;
import org.dasein.cloud.util.NamingConstraints;
import org.dasein.util.uom.storage.*;
import org.dasein.util.uom.storage.Byte;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Locale;

public class DriveSupport extends AbstractBlobStoreSupport{
   private Google provider;

    public DriveSupport(Google provider) {
        this.provider = provider;
    }

    @Override protected void get(@Nullable String bucket, @Nonnull String object, @Nonnull File toFile, @Nullable FileTransfer transfer) throws InternalException, CloudException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override protected void put(@Nullable String bucket, @Nonnull String objectName, @Nonnull File file) throws InternalException, CloudException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override protected void put(@Nullable String bucketName, @Nonnull String objectName, @Nonnull String content) throws InternalException, CloudException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean allowsNestedBuckets() throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean allowsRootObjects() throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean allowsPublicSharing() throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Blob createBucket(@Nonnull String bucket, boolean findFreeName) throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean exists(@Nonnull String bucket) throws InternalException, CloudException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public Blob getBucket(@Nonnull String bucketName) throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public Blob getObject(@Nullable String bucketName, @Nonnull String objectName) throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nullable @Override public String getSignedObjectUrl(@Nonnull String bucket, @Nonnull String object, @Nonnull String expiresEpochInSeconds) throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nullable @Override public Storage<Byte> getObjectSize(@Nullable String bucketName, @Nullable String objectName) throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public int getMaxBuckets() throws CloudException, InternalException{
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public Storage<Byte> getMaxObjectSize() throws InternalException, CloudException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public int getMaxObjectsPerBucket() throws CloudException, InternalException{
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public NamingConstraints getBucketNameRules() throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public NamingConstraints getObjectNameRules() throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public String getProviderTermForBucket(@Nonnull Locale locale){
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public String getProviderTermForObject(@Nonnull Locale locale){
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean isPublic(@Nullable String bucket, @Nullable String object) throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean isSubscribed() throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Iterable<Blob> list(@Nullable String bucket) throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public void makePublic(@Nonnull String bucket) throws InternalException, CloudException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public void makePublic(@Nullable String bucket, @Nonnull String object) throws InternalException, CloudException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public void move(@Nullable String fromBucket, @Nullable String objectName, @Nullable String toBucket) throws InternalException, CloudException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public void removeBucket(@Nonnull String bucket) throws CloudException, InternalException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public void removeObject(@Nullable String bucket, @Nonnull String object) throws CloudException, InternalException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public String renameBucket(@Nonnull String oldName, @Nonnull String newName, boolean findFreeName) throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public void renameObject(@Nullable String bucket, @Nonnull String oldName, @Nonnull String newName) throws CloudException, InternalException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public Blob upload(@Nonnull File sourceFile, @Nullable String bucket, @Nonnull String objectName) throws CloudException, InternalException{
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull @Override public String[] mapServiceAction(@Nonnull ServiceAction action){
        return new String[0];  //To change body of implemented methods use File | Settings | File Templates.
    }
}
