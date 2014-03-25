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

import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.storage.Storage;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.google.compute.GoogleCompute;
import org.dasein.cloud.google.network.GoogleNetwork;
import org.dasein.cloud.google.storage.GoogleDrive;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Support for the Google API through Dasein Cloud.
 * <p>Created by George Reese: 12/06/2012 9:35 AM</p>
 * @author George Reese
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class Google extends AbstractCloud {
	static private final Logger logger = getLogger(Google.class);

    private static final String DSN_P12_CERT = "p12Certificate";
    private static final String DSN_SERVICE_ACCOUNT = "serviceAccount";

	public final static String ISO8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
	public final static String ISO8601_NO_MS_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	
	static private @Nonnull String getLastItem(@Nonnull String name) {
		int idx = name.lastIndexOf('.');

		if( idx < 0 ) {
			return name;
		}
		else if( idx == (name.length()-1) ) {
			return "";
		}
		return name.substring(idx + 1);
	}

	static public @Nonnull Logger getLogger(@Nonnull Class<?> cls) {
		String pkg = getLastItem(cls.getPackage().getName());

		if( pkg.equals("google") ) {
			pkg = "";
		}
		else {
			pkg = pkg + ".";
		}
		return Logger.getLogger("dasein.cloud.google.std." + pkg + getLastItem(cls.getName()));
	}

	static public @Nonnull Logger getWireLogger(@Nonnull Class<?> cls) {
		return Logger.getLogger("dasein.cloud.google.wire." + getLastItem(cls.getPackage().getName()) + "." + getLastItem(cls.getName()));
	}

	public Google() { }

	@Override
	public @Nonnull String getCloudName() {
		ProviderContext ctx = getContext();
		String name = (ctx == null ? null : ctx.getCloudName());

		return (name == null ? "Google" : name);
	}

    @Override
    public @Nonnull ContextRequirements getContextRequirements() {
        return new ContextRequirements(
                new ContextRequirements.Field(DSN_P12_CERT, "The p12 file for the account", ContextRequirements.FieldType.KEYPAIR, ContextRequirements.Field.X509, true),
                new ContextRequirements.Field(DSN_SERVICE_ACCOUNT, "The service account email registered to the account", ContextRequirements.FieldType.TEXT, ContextRequirements.Field.ACCESS_KEYS, true)
        );
    }

	@Override
	public @Nonnull DataCenters getDataCenterServices() {
		return new DataCenters(this);
	}

	@Override
	public @Nonnull GoogleCompute getComputeServices() {
		return new GoogleCompute(this);
	}

    @Override
    public @Nonnull GoogleNetwork getNetworkServices() {
        return new GoogleNetwork(this);
    }

    @Override
    public @Nonnull GoogleDrive getStorageServices(){
        return new GoogleDrive(this);
    }

	@Override
	public @Nonnull String getProviderName() {
		ProviderContext ctx = getContext();
		String name = (ctx == null ? null : ctx.getProviderName());

		return (name == null ? "Google" : name);
	}

    public Compute getGoogleCompute() throws CloudException, InternalException {
        ProviderContext ctx = getContext();

        Cache<Compute> cache = Cache.getInstance(this, "ComputeAccess", Compute.class, CacheLevel.CLOUD, new TimePeriod<Hour>(1, TimePeriod.HOUR));
        Collection<Compute> googleCompute = (Collection<Compute>)cache.get(ctx);
        Compute gce = null;

        if (googleCompute == null) {
            googleCompute = new ArrayList<Compute>();
            HttpTransport transport = new NetHttpTransport();
            JsonFactory jsonFactory = new JacksonFactory();

            try{
                String serviceAccountId = "";
                byte[] p12Bytes = null;
                String p12Password = "";

                List<ContextRequirements.Field> fields = getContextRequirements().getConfigurableValues();
                for(ContextRequirements.Field f : fields ) {
                    if(f.type.equals(ContextRequirements.FieldType.KEYPAIR)){
                        byte[][] keyPair = (byte[][])getContext().getConfigurationValue(f);
                        p12Bytes = keyPair[0];
                        p12Password = new String(keyPair[1], "utf-8");
                    }
                    else if(f.type.equals(ContextRequirements.FieldType.TEXT)){
                        serviceAccountId = (String)getContext().getConfigurationValue(f);
                    }
                }

                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                InputStream p12AsStream = new ByteArrayInputStream(p12Bytes);
                keyStore.load(p12AsStream, p12Password.toCharArray());

                GoogleCredential creds = new GoogleCredential.Builder().setTransport(transport)
                        .setJsonFactory(jsonFactory)
                        .setServiceAccountId(serviceAccountId)
                        .setServiceAccountScopes(ComputeScopes.all())
                        .setServiceAccountPrivateKey((PrivateKey) keyStore.getKey("privateKey", p12Password.toCharArray()))//This is always the password for p12 files
                        .build();
                creds.setExpirationTimeMilliseconds(3600000L);

                gce = new Compute.Builder(transport, jsonFactory, creds).setApplicationName(ctx.getAccountNumber()).setHttpRequestInitializer(creds).build();
                googleCompute.add(gce);
                cache.put(ctx, googleCompute);
            }
            catch(Exception ex){
                ex.printStackTrace();
                throw new CloudException(CloudErrorType.AUTHENTICATION, 400, "Bad Credentials", "An authentication error has occurred: Bad Credentials");
            }
        }
        else{
            gce = googleCompute.iterator().next();
        }
        return gce;
    }

    public Storage getGoogleStorage() throws CloudException, InternalException{
        ProviderContext ctx = getContext();

        Cache<Storage> cache = Cache.getInstance(this, "DriveAccess", Storage.class, CacheLevel.CLOUD, new TimePeriod<Hour>(1, TimePeriod.HOUR));
        Collection<Storage> googleDrive = (Collection<Storage>)cache.get(ctx);
        Storage drive = null;

        if(googleDrive == null){
            googleDrive = new ArrayList<Storage>();

            HttpTransport transport = new NetHttpTransport();
            JsonFactory jsonFactory = new JacksonFactory();

            try{
                String serviceAccountId = "";
                byte[] p12Bytes = null;
                String p12Password = "";

                List<ContextRequirements.Field> fields = getContextRequirements().getConfigurableValues();
                for(ContextRequirements.Field f : fields ) {
                    if(f.type.equals(ContextRequirements.FieldType.KEYPAIR)){
                        byte[][] keyPair = (byte[][])getContext().getConfigurationValue(f);
                        p12Bytes = keyPair[0];
                        p12Password = new String(keyPair[1], "utf-8");
                    }
                    else if(f.type.equals(ContextRequirements.FieldType.TEXT)){
                        serviceAccountId = (String)getContext().getConfigurationValue(f);
                    }
                }

                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                InputStream p12AsStream = new ByteArrayInputStream(p12Bytes);
                keyStore.load(p12AsStream, p12Password.toCharArray());

                GoogleCredential creds = new GoogleCredential.Builder().setTransport(transport)
                        .setJsonFactory(jsonFactory)
                        .setServiceAccountId(serviceAccountId)
                        .setServiceAccountScopes(ComputeScopes.all())
                        .setServiceAccountPrivateKey((PrivateKey) keyStore.getKey("privateKey", p12Password.toCharArray()))//This is always the password for p12 files
                        .build();
                creds.setExpirationTimeMilliseconds(3600000L);

                drive = new Storage.Builder(transport, jsonFactory, creds).setApplicationName(ctx.getAccountNumber()).setHttpRequestInitializer(creds).build();
                googleDrive.add(drive);
                cache.put(ctx, googleDrive);
            }
            catch(Exception ex){
                ex.printStackTrace();
                throw new CloudException(CloudErrorType.AUTHENTICATION, 400, "Bad Credentials", "An authentication error has occurred: Bad Credentials");
            }
        }
        else{
            drive = googleDrive.iterator().next();
        }
        return drive;
    }

	@Override
    public @Nullable String testContext() {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER - " + Google.class.getName() + ".testContext()");
        }
        try {
            ProviderContext ctx = getContext();

            if( ctx == null ) {
                return null;
            }
            if (ctx.getRegionId() == null) {
                Collection<Region> regions = getDataCenterServices().listRegions();
                if (regions.size() > 0) {
                    ctx.setRegionId(regions.iterator().next().getProviderRegionId());
                }
            }

            if( !getComputeServices().getVirtualMachineSupport().isSubscribed() ) {
                return null;
            }
            return ctx.getAccountNumber();
        }
        catch( Throwable t ) {
            logger.error("Error querying API key: " + t.getMessage());
            t.printStackTrace();
            return null;
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT - " + Google.class.getName() + ".textContext()");
            }
        }
    }
}