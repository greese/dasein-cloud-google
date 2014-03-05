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
import java.text.SimpleDateFormat;
import java.util.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Zone;
import com.google.api.services.compute.model.ZoneList;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.google.compute.GoogleCompute;
import org.dasein.cloud.google.network.GoogleNetwork;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.ParseException;

/**
 * Support for the Google API through Dasein Cloud.
 * <p>Created by George Reese: 12/06/2012 9:35 AM</p>
 * @author George Reese
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class Google extends AbstractCloud {
	static private final Logger logger = getLogger(Google.class);

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
	public @Nonnull DataCenters getDataCenterServices() {
		return new DataCenters(this);
	}

	@Override
	public GoogleCompute getComputeServices() {
		return new GoogleCompute(this);
	}

		@Override
		public GoogleNetwork getNetworkServices() {
			return new GoogleNetwork(this);
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
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                InputStream p12AsStream = new ByteArrayInputStream(ctx.getAccessPrivate());
                keyStore.load(p12AsStream, "notasecret".toCharArray());

                GoogleCredential creds = new GoogleCredential.Builder().setTransport(transport)
                        .setJsonFactory(jsonFactory)
                        .setServiceAccountId(new String(ctx.getAccessPublic()))
                        .setServiceAccountScopes(ComputeScopes.all())
                        .setServiceAccountPrivateKey((PrivateKey) keyStore.getKey("privateKey", "notasecret".toCharArray()))//This is always the password for p12 files
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