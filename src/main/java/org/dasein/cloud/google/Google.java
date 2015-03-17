/**
 * Copyright (C) 2012-2014 Dell, Inc
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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.SQLAdminScopes;
import com.google.api.services.storage.Storage;

import org.apache.log4j.Logger;

import org.dasein.cloud.AbstractCloud;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ContextRequirements;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.google.compute.GoogleCompute;
import org.dasein.cloud.google.network.GoogleNetwork;
import org.dasein.cloud.google.platform.GooglePlatform;
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
    public static final Set<String> sqlScope = new HashSet<String>(Arrays.asList(SQLAdminScopes.CLOUD_PLATFORM,SQLAdminScopes.SQLSERVICE_ADMIN));

    private final static CustomHttpRequestInitializer initializer = new CustomHttpRequestInitializer();

    private JsonFactory jsonFactory = null;

    private Cache<GoogleCredential> cachedCredentials = null;
    private Cache<GoogleCredential> cachedSqlCredentials = null;
    private Cache<Compute> computeCache = null;
    private Cache<Storage> storageCache = null;
    private Cache<SQLAdmin> sqlCache = null;

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

    public Google() {
        jsonFactory = new JacksonFactory();
        cachedCredentials = Cache.getInstance(this, "Credentials", GoogleCredential.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(1, TimePeriod.HOUR));
        cachedSqlCredentials = Cache.getInstance(this, "SqlCredentials", GoogleCredential.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(1, TimePeriod.HOUR));
        computeCache = Cache.getInstance(this, "ComputeAccess", Compute.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(1, TimePeriod.HOUR));
        storageCache = Cache.getInstance(this, "DriveAccess", Storage.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(1, TimePeriod.HOUR));
        sqlCache = Cache.getInstance(this, "SqlAccess", SQLAdmin.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(1, TimePeriod.HOUR));
    }

    @Override
    public @Nonnull String getCloudName() {
        ProviderContext ctx = getContext();
        String name = (ctx == null ? null : ctx.getCloudName());

        return (name == null ? "GCE" : name);
    }

    @Override
    public @Nonnull ContextRequirements getContextRequirements() {
        return new ContextRequirements(
                new ContextRequirements.Field(DSN_P12_CERT, "The p12 file for the account", ContextRequirements.FieldType.KEYPAIR, ContextRequirements.Field.X509, true),
                new ContextRequirements.Field(DSN_SERVICE_ACCOUNT, "The service account email registered to the account", ContextRequirements.FieldType.TEXT, ContextRequirements.Field.ACCESS_KEYS, true),
                new ContextRequirements.Field("proxyHost", "Proxy host", ContextRequirements.FieldType.TEXT, null, false),
                new ContextRequirements.Field("proxyPort", "Proxy port", ContextRequirements.FieldType.TEXT, null, false)
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
    public @Nullable GooglePlatform getPlatformServices() {
        return new GooglePlatform(this);
    }

    public @Nullable String getProxyHost() {
        ProviderContext ctx = getContext();

        if( ctx == null ) {
            return null;
        }
        Properties props = ctx.getCustomProperties();

        return ( props == null ? null : props.getProperty("proxyHost") );
    }

    public int getProxyPort() {
        ProviderContext ctx = getContext();

        if( ctx == null ) {
            return -1;
        }
        Properties props = ctx.getCustomProperties();

        if( props == null ) {
            return -1;
        }
        String port = props.getProperty("proxyPort");

        if( port != null ) {
            return Integer.parseInt(port);
        }
        return -1;
    }

    @Override
    public @Nonnull GoogleDrive getStorageServices(){
        return new GoogleDrive(this);
    }

    @Override
    public @Nonnull String getProviderName() {
        ProviderContext ctx = getContext();
        String name = (ctx == null ? null : ctx.getCloud().getCloudName());

        return (name == null ? "Google" : name);
    }

    private HttpTransport getTransport() {
        HttpTransport transport = null;
        int proxyPort = -1;
        String proxyHost = null;

        List<ContextRequirements.Field> fields = getContextRequirements().getConfigurableValues();
        for(ContextRequirements.Field f : fields )
            if ((f.compatName == null) && (f.name.equals("proxyHost")))
                proxyHost = getProxyHost();
            else if ((f.compatName == null) && (f.name.equals("proxyPort")))
                proxyPort = getProxyPort();

        if ( proxyHost != null && proxyHost.length() > 0 && proxyPort > 0 ) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            transport = new NetHttpTransport.Builder().setProxy(proxy).build();
        } else
            transport = new NetHttpTransport();
        return transport;
    }

    private GoogleCredential getCreds(HttpTransport transport, JsonFactory jsonFactory, Collection<String> scopes) throws Exception {
        byte[] p12Bytes = null;
        String p12Password = "";

        String serviceAccountId = "";

        List<ContextRequirements.Field> fields = getContextRequirements().getConfigurableValues();
        try {
            for(ContextRequirements.Field f : fields ) {
                if(f.type.equals(ContextRequirements.FieldType.KEYPAIR)){
                    byte[][] keyPair = (byte[][])getContext().getConfigurationValue(f);
                    p12Bytes = keyPair[0];
                    p12Password = new String(keyPair[1], "utf-8");
                } else if(f.compatName != null && f.compatName.equals(ContextRequirements.Field.ACCESS_KEYS))
                    serviceAccountId = (String)getContext().getConfigurationValue(f);
            }
        } catch(Exception ex) {
                throw new CloudException(CloudErrorType.AUTHENTICATION, 400, "Bad Credentials", "An authentication error has occurred: Bad Credentials");
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        InputStream p12AsStream = new ByteArrayInputStream(p12Bytes);
        keyStore.load(p12AsStream, p12Password.toCharArray());
        GoogleCredential creds = new GoogleCredential.Builder().setTransport(transport)
                .setJsonFactory(jsonFactory)
                .setServiceAccountId(serviceAccountId)
                .setServiceAccountScopes(scopes)
                .setServiceAccountPrivateKey((PrivateKey) keyStore.getKey("privateKey", p12Password.toCharArray()))//This is always the password for p12 files
                .build();

        return creds;
    }

    public Compute getGoogleCompute() throws CloudException, InternalException {
        ProviderContext ctx = getContext();
        Collection<GoogleCredential> cachedCredential = (Collection<GoogleCredential>)cachedCredentials.get(ctx);
        Collection<Compute> googleCompute = (Collection<Compute>)computeCache.get(ctx);
        try {
            final HttpTransport transport = getTransport();
            if (cachedCredential == null || googleCompute == null) {
                cachedCredential = new ArrayList<GoogleCredential>();
                cachedCredential.add(getCreds(transport, jsonFactory, ComputeScopes.all()));
                cachedCredentials.put(ctx, cachedCredential);
            }

            if (googleCompute == null) {
                googleCompute = new ArrayList<Compute>();
                googleCompute.add((Compute) new Compute.Builder(transport, jsonFactory, cachedCredential.iterator().next()).setApplicationName(ctx.getAccountNumber()).setHttpRequestInitializer(initializer).build());
                computeCache.put(ctx, googleCompute);
            }
        } catch(Exception ex) {
            throw new CloudException(CloudErrorType.AUTHENTICATION, 400, "Bad Credentials", "An authentication error has occurred: Bad Credentials");
        }

        initializer.setStackedRequestInitializer(ctx, cachedCredential.iterator().next());
        LogHandler.verifyInitialized();

        return googleCompute.iterator().next();
    }

    public Storage getGoogleStorage() throws CloudException, InternalException{
        ProviderContext ctx = getContext();
        Collection<GoogleCredential> cachedCredential = (Collection<GoogleCredential>)cachedCredentials.get(ctx);
        Collection<Storage> googleDrive = (Collection<Storage>)storageCache.get(ctx);
        try {
            final HttpTransport transport = getTransport();
            if (cachedCredential == null || googleDrive == null) {
                cachedCredential = new ArrayList<GoogleCredential>();
                cachedCredential.add(getCreds(transport, jsonFactory, ComputeScopes.all()));
                cachedCredentials.put(ctx, cachedCredential);
            }

            if (googleDrive == null) {
                googleDrive = new ArrayList<Storage>();
                googleDrive.add((Storage) new Storage.Builder(transport, jsonFactory, cachedCredential.iterator().next()).setApplicationName(ctx.getAccountNumber()).setHttpRequestInitializer(initializer).build());
                storageCache.put(ctx, googleDrive);
            }
        } catch(Exception ex) {
            throw new CloudException(CloudErrorType.AUTHENTICATION, 400, "Bad Credentials", "An authentication error has occurred: Bad Credentials");
        }

        initializer.setStackedRequestInitializer(ctx, cachedCredential.iterator().next());
        LogHandler.verifyInitialized();

        return googleDrive.iterator().next();
    }

    public SQLAdmin getGoogleSQLAdmin() throws CloudException, InternalException{
        ProviderContext ctx = getContext();
        Collection<GoogleCredential> cachedSqlCredential = (Collection<GoogleCredential>)cachedSqlCredentials.get(ctx);
        Collection<SQLAdmin> googleSql = (Collection<SQLAdmin>)sqlCache.get(ctx);
        try {
            final HttpTransport transport = getTransport();
            if (cachedSqlCredential == null) {
                cachedSqlCredential = new ArrayList<GoogleCredential>();
                cachedSqlCredential.add(getCreds(transport, jsonFactory, sqlScope));
                cachedSqlCredentials.put(ctx, cachedSqlCredential);
            }

            if (googleSql == null) {
                googleSql = new ArrayList<SQLAdmin>();
                googleSql.add((SQLAdmin) new SQLAdmin.Builder(transport, jsonFactory, cachedSqlCredential.iterator().next()).setApplicationName(ctx.getAccountNumber()).setHttpRequestInitializer(initializer).build());
                sqlCache.put(ctx, googleSql);
            }
        } catch (Exception ex){
            throw new CloudException(CloudErrorType.AUTHENTICATION, 400, "Bad Credentials", "An authentication error has occurred: Bad Credentials");
        }

        initializer.setStackedRequestInitializer(ctx, cachedSqlCredential.iterator().next());
        LogHandler.verifyInitialized();

        return googleSql.iterator().next();
    }

    @Override
    public @Nullable String testContext() {
        if (logger.isTraceEnabled())
            logger.trace("ENTER - " + Google.class.getName() + ".testContext()");

        NetHttpTransport httpTransport2 = new NetHttpTransport();

        JacksonFactory jsonFactory2 = new JacksonFactory();

        ProviderContext ctx = getContext();
        if (ctx == null)
            return null;

        try {
            GoogleCredential creds = null;
            Compute googleCompute = null;

            try {
                creds = getCreds(httpTransport2, jsonFactory2, ComputeScopes.all());
                googleCompute = new Compute.Builder(httpTransport2, jsonFactory2, creds).setApplicationName(ctx.getAccountNumber()).build();
                googleCompute.networks().list(ctx.getAccountNumber()).execute();

                return ctx.getAccountNumber();
            } catch (Exception e) {
                logger.error("Error list firewalls failed: ");
                return null;
            }
        } finally {
            if (logger.isTraceEnabled())
                logger.trace("EXIT - " + Google.class.getName() + ".textContext()");
        }
    }

    public long parseTime(@Nullable String time) throws CloudException {
        if (time == null) {
            return 0L;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

        if (time.length() > 0) {
            try {
                return fmt.parse(time).getTime();
            } catch (ParseException e) {
                fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                try {
                    return fmt.parse(time).getTime();
                } catch (ParseException encore) {
                    throw new CloudException("Could not parse date: " + time);
                }
            }
        }
        return 0L;
    }
}
