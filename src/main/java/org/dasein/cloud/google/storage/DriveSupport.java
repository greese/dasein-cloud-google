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

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.model.Bucket;
import com.google.api.services.storage.model.Buckets;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import org.apache.log4j.Logger;
import org.dasein.cloud.Capabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.storage.AbstractBlobStoreSupport;
import org.dasein.cloud.storage.Blob;
import org.dasein.cloud.storage.FileTransfer;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.NamingConstraints;
import org.dasein.util.uom.storage.Byte;
import org.dasein.util.uom.storage.Storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;

public class DriveSupport extends AbstractBlobStoreSupport{
   private Google provider;
    static private final Logger logger = Google.getLogger(DriveSupport.class);

    public DriveSupport(Google provider) {
        this.provider = provider;
    }

    @Override protected void get(@Nullable String bucket, @Nonnull String object, @Nonnull File toFile, @Nullable FileTransfer transfer) throws InternalException, CloudException{
        APITrace.begin(provider, "downloadObject");
        try {
            try {
                if( bucket == null ) {
                    logger.error("No bucket was specified for download file request");
                    throw new OperationNotSupportedException("No bucket was specified for download file request");
                }
                InputStream input;
                com.google.api.services.storage.Storage storage = provider.getGoogleStorage();
                com.google.api.services.storage.Storage.Objects.Get getObject = storage.objects().get(bucket, object);
                // Downloading data.
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                // If you're not in AppEngine, download the whole thing in one request, if possible.
                getObject.getMediaHttpDownloader().setDirectDownloadEnabled(true);
                getObject.executeMediaAndDownloadTo(out);
                input = new ByteArrayInputStream(out.toByteArray());

                try {
                    copy(input, new FileOutputStream(toFile), transfer);
                }
                catch( FileNotFoundException e ) {
                    logger.error("Could not find target file to fetch to " + toFile + ": " + e.getMessage());
                    throw new InternalException(e);
                }
                catch( IOException e ) {
                    logger.error("Could not fetch file to " + toFile + ": " + e.getMessage());
                    throw new CloudException(e);
                }
            }
            catch (IOException e) {
                throw new InternalException(e);
            }
        }
        finally {
            APITrace.end();
        }

    }

    @Override protected void put(@Nullable String bucket, @Nonnull String objectName, @Nonnull File file) throws InternalException, CloudException{
        try {
            if( bucket == null ) {
                logger.error("No bucket was specified for upload file request");
                throw new OperationNotSupportedException("No bucket was specified for upload file request");
            }
            com.google.api.services.storage.Storage storage = provider.getGoogleStorage();

            InputStream inputStream = new FileInputStream(file);  // object data, e.g., FileInputStream
            long byteCount = file.length();  // size of input stream

            InputStreamContent mediaContent = new InputStreamContent("application/octet-stream", inputStream);
            // Knowing the stream length allows server-side optimization, and client-side progress
            // reporting with a MediaHttpUploaderProgressListener.
            mediaContent.setLength(byteCount);

            StorageObject objectMetadata = null;

            com.google.api.services.storage.Storage.Objects.Insert insertObject = storage.objects().insert(bucket, objectMetadata,
                    mediaContent);

            insertObject.setName(objectName);

            // For small files, you may wish to call setDirectUploadEnabled(true), to
            // reduce the number of HTTP requests made to the server.
            if (mediaContent.getLength() > 0 && mediaContent.getLength() <= 2 * 1000 * 1000 /* 2MB */) {
                insertObject.getMediaHttpUploader().setDirectUploadEnabled(true);
            }

            insertObject.execute();
        }
        catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override protected void put(@Nullable String bucketName, @Nonnull String objectName, @Nonnull String content) throws InternalException, CloudException{
        APITrace.begin(provider, "Blob.put");
        try {
            if( bucketName == null ) {
                logger.error("No bucket was specified for upload file request");
                throw new OperationNotSupportedException("No bucket was specified for upload file request");
            }
            try {
                File tmp = File.createTempFile(objectName, ".txt");
                PrintWriter writer;

                try {
                    writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tmp)));
                    writer.print(content);
                    writer.flush();
                    writer.close();
                    put(bucketName, objectName, tmp);
                }
                finally {
                    if( !tmp.delete() ) {
                        logger.warn("Unable to delete temp file: " + tmp);
                    }
                }
            }
            catch( IOException e ) {
                logger.error("Failed to write file: " + e.getMessage());
                e.printStackTrace();
                throw new InternalException(e);
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override public boolean allowsNestedBuckets() throws CloudException, InternalException{
        return false;
    }

    @Override public boolean allowsRootObjects() throws CloudException, InternalException{
        return false;
    }

    @Override public boolean allowsPublicSharing() throws CloudException, InternalException{
        return true;
    }

    @Nonnull @Override public Blob createBucket(@Nonnull String bucket, boolean findFreeName) throws InternalException, CloudException{
        APITrace.begin(provider, "createBucket");
        try {
            try {
                ProviderContext ctx = provider.getContext();
                if (ctx == null) {
                    throw new InternalException("Context is null");
                }
                String projectId = ctx.getAccountNumber();
                com.google.api.services.storage.Storage storage = provider.getGoogleStorage();
                Bucket newBucket = storage.buckets().insert(projectId, new Bucket().setName(bucket)).execute();
                return toBucket(newBucket);
            }
            catch (IOException e) {
                throw new InternalException(e);
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override public boolean exists(@Nonnull String bucket) throws InternalException, CloudException{
        try {
            com.google.api.services.storage.Storage storage = provider.getGoogleStorage();
            Bucket myBucket = storage.buckets().get(bucket).execute();
            if (myBucket != null) {
                return true;
            }
            return false;
        }
        catch (Throwable ignore) {
            return false;
        }
    }

    @Override public Blob getBucket(@Nonnull String bucketName) throws InternalException, CloudException{
        APITrace.begin(provider, "getBucket");
        try {
            try{
                com.google.api.services.storage.Storage storage = provider.getGoogleStorage();
                Bucket myBucket = storage.buckets().get(bucketName).execute();
                if (myBucket != null) {
                    Blob blob = toBucket(myBucket);
                    return blob;
                }
                return null;
            }
            catch (IOException ex) {
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred when getting bucket: " + bucketName + ": " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override public Blob getObject(@Nullable String bucketName, @Nonnull String objectName) throws InternalException, CloudException{
        APITrace.begin(provider, "getObject");
        try {
            if( bucketName == null ) {
                logger.error("No bucket was specified for get object request");
                throw new OperationNotSupportedException("No bucket was specified for get object request");
            }
            try{
                com.google.api.services.storage.Storage storage = provider.getGoogleStorage();
                StorageObject myObject = storage.objects().get(bucketName, objectName).execute();
                Blob blob = toObject(myObject);
                return blob;
            }
            catch (IOException ex) {
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred when getting bucket: " + bucketName + ": " + ex.getMessage());
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Nullable @Override public String getSignedObjectUrl(@Nonnull String bucket, @Nonnull String object, @Nonnull String expiresEpochInSeconds) throws InternalException, CloudException{
        throw new OperationNotSupportedException("Signed object URLs are not currently supported.");
    }

    @Nullable @Override public Storage<Byte> getObjectSize(@Nullable String bucketName, @Nullable String objectName) throws InternalException, CloudException{
        if( bucketName == null ) {
            logger.error("No bucket was specified for get object size request");
            throw new OperationNotSupportedException("No bucket was specified for get object request");
        }
        if( objectName == null ) {
            logger.error("No object was specified for get object size request");
            throw new OperationNotSupportedException("No object was specified for get object request");
        }
        Blob blob = getObject(bucketName, objectName);
        if (blob != null) {
            return blob.getSize();
        }
        return null;
    }

    @Override public int getMaxBuckets() throws CloudException, InternalException{
        return Capabilities.LIMIT_UNLIMITED;
    }

    @Override public Storage<Byte> getMaxObjectSize() throws InternalException, CloudException{
        return new Storage<Byte>(Capabilities.LIMIT_UNKNOWN, Storage.BYTE);
    }

    @Override public int getMaxObjectsPerBucket() throws CloudException, InternalException{
        return Capabilities.LIMIT_UNLIMITED;
    }

    @Nonnull @Override public NamingConstraints getBucketNameRules() throws CloudException, InternalException{
        return NamingConstraints.getAlphaNumeric(3,222).lowerCaseOnly().constrainedBy(new char[]{'-', '_', '.'});
    }

    @Nonnull @Override public NamingConstraints getObjectNameRules() throws CloudException, InternalException{
        return NamingConstraints.getAlphaNumeric(1, 255);
    }

    @Nonnull @Override public String getProviderTermForBucket(@Nonnull Locale locale){
        return "bucket";
    }

    @Nonnull @Override public String getProviderTermForObject(@Nonnull Locale locale){
        return "object";
    }

    @Override public boolean isPublic(@Nullable String bucket, @Nullable String object) throws CloudException, InternalException{
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public boolean isSubscribed() throws CloudException, InternalException{
        return true;
    }

    @Nonnull @Override public Iterable<Blob> list(@Nullable String bucket) throws CloudException, InternalException{
        APITrace.begin(provider, "list");
        try {
            ProviderContext ctx = provider.getContext();
            if (ctx == null) {
                throw new InternalException("Context is null");
            }
            try {
                ArrayList<Blob> list = new ArrayList<Blob>();
                com.google.api.services.storage.Storage storage = provider.getGoogleStorage();
                if (bucket == null) {
                    Buckets buckets = storage.buckets().list(ctx.getAccountNumber()).execute();
                    for (int i = 0; i<buckets.size(); i++) {
                        Blob blob = toBucket(buckets.getItems().get(i));
                        if (blob != null) {
                            list.add(blob);
                        }
                    }
                }
                else {
                    Objects objects = storage.objects().list(bucket).execute();
                    for (int i = 0; i<objects.size(); i++) {
                        Blob blob = toObject(objects.getItems().get(i));
                        if (blob != null) {
                            list.add(blob);
                        }
                    }
                }
                return list;
            }
            catch (IOException e){
                throw new InternalException(e);
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override public void makePublic(@Nonnull String bucket) throws InternalException, CloudException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public void makePublic(@Nullable String bucket, @Nonnull String object) throws InternalException, CloudException{
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override public void move(@Nullable String fromBucket, @Nullable String objectName, @Nullable String toBucket) throws InternalException, CloudException{
        if (fromBucket == null || toBucket == null) {
            throw new InternalException("One or both buckets not specified");
        }
        if (objectName == null)  {
            throw new InternalException("Object name is not specified");
        }
        copyFile(fromBucket, objectName, toBucket, objectName);
        removeObject(fromBucket, objectName);
    }

    @Override public void removeBucket(@Nonnull String bucket) throws CloudException, InternalException{
        //first of all we need to remove the objects
        for (Blob blob : list(bucket)) {
            removeObject(bucket, blob.getObjectName());
        }

        try {
            com.google.api.services.storage.Storage storage = provider.getGoogleStorage();
            storage.buckets().delete(bucket).execute();
        }
        catch (IOException e) {
            throw new InternalException(e);
        }

    }

    @Override public void removeObject(@Nullable String bucket, @Nonnull String object) throws CloudException, InternalException{
        if (bucket == null) {
           throw new InternalException("Bucket is null for remove object request");
        }
        try {
            com.google.api.services.storage.Storage storage = provider.getGoogleStorage();
            storage.objects().delete(bucket, object).execute();
        }
        catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Nonnull @Override public String renameBucket(@Nonnull String oldName, @Nonnull String newName, boolean findFreeName) throws CloudException, InternalException{
        copy(oldName, null, newName, null);
        removeBucket(oldName);
        return newName;
    }

    @Override public void renameObject(@Nullable String bucket, @Nonnull String oldName, @Nonnull String newName) throws CloudException, InternalException{
        if( bucket == null ) {
            throw new CloudException("No bucket was specified");
        }
        copy(bucket, oldName, bucket, newName);
        removeObject(bucket, oldName);
    }

    @Nonnull @Override public Blob upload(@Nonnull File sourceFile, @Nullable String bucket, @Nonnull String objectName) throws CloudException, InternalException{
        APITrace.begin(provider, "uploadFile");
        try {
            if( bucket == null ) {
                logger.error("No bucket was specified for this request");
                throw new OperationNotSupportedException("No bucket was specified for this request");
            }
            if( !exists(bucket) ) {
                createBucket(bucket, false);

            }
            put(bucket, objectName, sourceFile);
            return getObject(bucket, objectName);
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull @Override public String[] mapServiceAction(@Nonnull ServiceAction action){
        return new String[0];
    }

    private Blob toBucket(Bucket bucket) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        if (ctx == null) {
            throw new InternalException("Context is null");
        }
        String regionId = ctx.getRegionId();
        if (regionId == null) {
            throw new InternalException("Regiond cannot be null");
        }
        String name = bucket.getName();
        long creationDate = bucket.getTimeCreated().getValue();
        String location = bucket.getLocation();

        return Blob.getInstance(regionId, location, name, creationDate);
    }

    private Blob toObject(StorageObject object) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        if (ctx == null) {
            throw new InternalException("Context is null");
        }
        String regionId = ctx.getRegionId();
        if (regionId == null) {
            throw new InternalException("Regiond cannot be null");
        }
        String name = object.getName();
        String bucket = object.getBucket();
        long creationDate = object.getUpdated().getValue();
        String location = object.getSelfLink();
        long size = object.getSize().longValue();

        return Blob.getInstance(regionId, location, bucket, name, creationDate, new Storage<Byte>(size, Storage.BYTE));
    }
}
