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

package org.dasein.cloud.google.platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.DayOfWeek;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.TimeWindow;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.capabilities.GCERelationalDatabaseCapabilities;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.AbstractRelationalDatabaseSupport;
import org.dasein.cloud.platform.ConfigurationParameter;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseBackup;
import org.dasein.cloud.platform.DatabaseBackupState;
import org.dasein.cloud.platform.DatabaseConfiguration;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseLicenseModel;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.DatabaseSnapshot;
import org.dasein.cloud.platform.DatabaseState;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Day;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.Second;
import org.dasein.util.uom.time.TimePeriod;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.AclEntry;
import com.google.api.services.sqladmin.model.BackupConfiguration;
import com.google.api.services.sqladmin.model.BackupRun;
import com.google.api.services.sqladmin.model.BackupRunsListResponse;
import com.google.api.services.sqladmin.model.CloneContext;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.Flag;
import com.google.api.services.sqladmin.model.FlagsListResponse;
import com.google.api.services.sqladmin.model.InstancesCloneRequest;
import com.google.api.services.sqladmin.model.InstancesListResponse;
import com.google.api.services.sqladmin.model.InstancesRestoreBackupRequest;
import com.google.api.services.sqladmin.model.IpConfiguration;
import com.google.api.services.sqladmin.model.LocationPreference;
import com.google.api.services.sqladmin.model.Operation;
import com.google.api.services.sqladmin.model.OperationError;
import com.google.api.services.sqladmin.model.OperationsListResponse;
import com.google.api.services.sqladmin.model.RestoreBackupContext;
import com.google.api.services.sqladmin.model.Settings;
import com.google.api.services.sqladmin.model.Tier;
import com.google.api.services.sqladmin.model.TiersListResponse;
import com.google.api.services.sqladmin.model.User;
/*
 * https://developers.google.com/cloud-sql/faq#data_location
 */

public class RDS extends AbstractRelationalDatabaseSupport<Google> {
    static private final Logger logger = Logger.getLogger(RDS.class);

    private Cache<JSONObject> jsonPriceList = null;
    private Cache<DatabaseInstance> listDatabasesInstanceCache = null;
    private Cache<Database> listDatabasesCache = null;
    private Cache<DatabaseEngine> databaseEngines = null;

    private Cache<Tier> tiersList = null;
    final static private String jsonPriceUrl = "http://cloudpricingcalculator.appspot.com/static/data/pricelist.json";
    static private Long gigabyte = 1073741824L;
    static private Long megabyte = 1048576L;
    private Google provider;

    RDS(Google provider) {
        super(provider);
        this.provider = provider;
        jsonPriceList = Cache.getInstance(provider, "jsonPriceList", JSONObject.class, CacheLevel.CLOUD, new TimePeriod<Hour>(1, TimePeriod.HOUR));
        databaseEngines = Cache.getInstance(provider, "databaseEngineList", DatabaseEngine.class, CacheLevel.CLOUD, new TimePeriod<Day>(1, TimePeriod.DAY));
        tiersList = Cache.getInstance(provider, "tierList", Tier.class, CacheLevel.CLOUD, new TimePeriod<Day>(1, TimePeriod.DAY));
        listDatabasesInstanceCache = Cache.getInstance(provider, "listDatabasesInstanceCache", DatabaseInstance.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Second>(60, TimePeriod.SECOND)); // disable for testing
        listDatabasesCache = Cache.getInstance(provider, "listDatabasesCache", Database.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Second>(30, TimePeriod.SECOND)); // disable for testing
    }

    public void handleGoogleException(Exception e) throws CloudException, InternalException  {
        if (e.getClass() == GoogleJsonResponseException.class) {
            GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
            throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
        } else
            throw new CloudException(e);
    }

    @Override
    public String[] mapServiceAction(ServiceAction action) {
        // IGNORE - Drew
        return new String[0];
    }

    @Override
    public void addAccess(String providerDatabaseId, String sourceCidr) throws CloudException, InternalException {
        if (sourceCidr.matches("[0-9][0-9./]*[0-9]")) {
            addAccessAuthorizedNetworks(providerDatabaseId, sourceCidr);
        } else
            addAccessAuthorizedGaeApplications(providerDatabaseId, sourceCidr);
    }

    private void addAccessAuthorizedNetworks(String providerDatabaseId, String sourceCidr) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        try {
            DatabaseInstance instance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();
            Settings settings = instance.getSettings();
            List<AclEntry> authorizedNetworks = settings.getIpConfiguration().getAuthorizedNetworks();
            if (authorizedNetworks == null)
                authorizedNetworks = new ArrayList<AclEntry>();

            AclEntry acl = new AclEntry();
            acl.setValue(sourceCidr);
            authorizedNetworks.add(acl);

            settings.getIpConfiguration().setAuthorizedNetworks(authorizedNetworks);
            instance.setSettings(settings);
            GoogleMethod method = new GoogleMethod(provider);
            Operation response = sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, instance).execute();
            method.getRDSOperationComplete(ctx, response.getName());
        } catch (Exception e) {
            handleGoogleException(e);
        }
    }

    public void addAccessAuthorizedGaeApplications(String providerDatabaseId, String authorizedApplication) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        try {
            DatabaseInstance instance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();
            if (instance != null) {
                Settings settings = instance.getSettings();
                List<String> authorizedApplications = settings.getAuthorizedGaeApplications();
                if (authorizedApplications == null) 
                    authorizedApplications = new ArrayList<String>();
                authorizedApplications.add(authorizedApplication);
                settings.setAuthorizedGaeApplications(authorizedApplications);
                instance.setSettings(settings);

                GoogleMethod method = new GoogleMethod(provider);
                Operation response = sqlAdmin.instances().patch(ctx.getAccountNumber(), providerDatabaseId, instance).execute();
                method.getRDSOperationComplete(ctx, response.getName());
            }
        } catch (Exception e) {
            handleGoogleException(e);
        }
    }

    @Override
    public void revokeAccess(String providerDatabaseId, String deauthedCidr) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        try {
            DatabaseInstance instance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();
            Settings settings = instance.getSettings();
            List<AclEntry> authorizedNetworks = settings.getIpConfiguration().getAuthorizedNetworks();
            if (authorizedNetworks == null)
                authorizedNetworks = new ArrayList<AclEntry>();
            else {
                for (AclEntry net : authorizedNetworks) {
                    if (deauthedCidr.equals(net.getValue())) {
                        authorizedNetworks.remove(net);
                        break;
                    }
                }
            }
            settings.getIpConfiguration().setAuthorizedNetworks(authorizedNetworks);
            GoogleMethod method = new GoogleMethod(provider);
            Operation response = sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, instance).execute();
            method.getRDSOperationComplete(ctx, response.getName());
        } catch (Exception e) {
            handleGoogleException(e);
        }
    }

    private void setPassword(@Nonnull String providerDatabaseId, @Nonnull String newAdminPassword) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        try {
            User content = new User();
            content.setPassword("password");
            content.setName("root");
            Operation response = sqlAdmin.users().insert(ctx.getAccountNumber(), providerDatabaseId, content).execute();

            GoogleMethod method = new GoogleMethod(provider);
            method.getRDSOperationComplete(ctx, response.getName());
        } catch (Exception e) {
            handleGoogleException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.dasein.cloud.platform.AbstractRelationalDatabaseSupport#alterDatabase(java.lang.String, boolean, java.lang.String, int, java.lang.String, java.lang.String, java.lang.String, int, int, org.dasein.cloud.TimeWindow, org.dasein.cloud.TimeWindow)
     * newPort - GCE does not support port re-mapping
     * newAdminUser - GCE does not expose changing mySql admin user name
     * configurationId - setting this may be inappropriate.
     * snapshotRetentionInDays - dbSnapshots are not currently supported for GCE as they require direct db access to restore.
     * configurationId - appears changing this really upsets the google
     * preferredMaintenanceWindow - does not appear to be supported
     */
    @Override
    public void alterDatabase(String providerDatabaseId, boolean applyImmediately, String productSize, int storageInGigabytes, String configurationId, String newAdminUser, String newAdminPassword, int newPort, int snapshotRetentionInDays, TimeWindow preferredMaintenanceWindow, TimeWindow preferredBackupWindow) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        DatabaseInstance databaseInstance = null;
        GoogleMethod method = new GoogleMethod(provider);

        try {
            databaseInstance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();
        } catch (Exception e) {
            handleGoogleException(e);
        }

        if (null == databaseInstance) 
            throw new CloudException("Database instance " + providerDatabaseId + " does not exist.");

        databaseInstance.setMaxDiskSize(storageInGigabytes * gigabyte);

        setPassword(providerDatabaseId, newAdminPassword);

        try {
            User content = new User();
            content.setPassword(newAdminPassword);
            content.setName(newAdminUser);

            String host = "0.0.0.0/32";
            Operation response = sqlAdmin.users().update(ctx.getAccountNumber(), providerDatabaseId, host, newAdminUser, content).execute();

            method.getRDSOperationComplete(ctx, response.getName());
        } catch (Exception e) {
            handleGoogleException(e);
        }

        Settings settings = databaseInstance.getSettings();

        BackupConfiguration updatedBackupConfiguration = null;
        if (null != settings.getBackupConfiguration()) {
            updatedBackupConfiguration = settings.getBackupConfiguration();
        } else {
            updatedBackupConfiguration = new BackupConfiguration();
            updatedBackupConfiguration.setKind("sql#backupConfiguration");
            updatedBackupConfiguration.setBinaryLogEnabled(true); // default to on (until implemented, enable by default)
            updatedBackupConfiguration.setEnabled(true);  // might as well default to turned on.
        }

        if (null != preferredBackupWindow) {
            String startTime = String.format("%02d", preferredBackupWindow.getStartHour()) + ":" + String.format("%02d", preferredBackupWindow.getStartMinute());
            updatedBackupConfiguration.setStartTime(startTime);
        }

        settings.setBackupConfiguration(updatedBackupConfiguration);
        if (null != productSize) {
            settings.setTier(productSize.toUpperCase());
        }

        databaseInstance.setSettings(settings);

        try {
            Operation response = sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, databaseInstance).execute();
            if (applyImmediately) 
                method.getRDSOperationComplete(ctx, response.getName());
        } catch (Exception e) {
            handleGoogleException(e);
        }
    }

    @Override
    public String createFromScratch(String dataSourceName, DatabaseProduct product, String databaseVersion, String withAdminUser, String withAdminPassword, int hostPort) throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.createFromScratch");
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        try {
            DatabaseInstance content = new DatabaseInstance();
            if (databaseVersion == null) {
                String newDatabaseVersion = getDefaultVersion(product.getEngine()).replaceAll("\\.", "_");
                content.setDatabaseVersion(product.getEngine().name() + "_" + newDatabaseVersion);
            } else {
                content.setDatabaseVersion(product.getEngine().name() + "_" + databaseVersion.replaceAll("\\.", "_"));
            }
            content.setName(dataSourceName);
            content.setProject(ctx.getAccountNumber());

            try {
                String regionId = ctx.getRegionId();
                if (regionId.equals("us-central1")) {
                    regionId = "us-central";  // fix for google inconsistency 
                }
                content.setRegion(regionId);
            } catch (NullPointerException npe) {
                logger.error("setRegion failed: " + npe);
            }

            try {
                if (null != product) {
                    content.setMaxDiskSize(product.getStorageInGigabytes() * gigabyte);
                }
            } catch (NullPointerException npe) {
                logger.error("setMaxDiskSize failed: " + npe);
            }

            Settings settings = new Settings();
            try {
                if (product.isHighAvailability()) {
                    settings.setActivationPolicy("ALWAYS");  // ALWAYS NEVER ON_DEMAND
                } else {
                    settings.setActivationPolicy("ON_DEMAND");
                }
            } catch (NullPointerException npe) {
                logger.error("setActivationPolicy failed: " + npe);
            }

            try {
                if (product.getName().contains("Daily")) {
                    settings.setPricingPlan("PACKAGE");
                } else if (product.getName().contains("Hourly")) {
                    settings.setPricingPlan("PER_USE");
                }
            } catch (NullPointerException npe) {
                logger.error("setPricingPlan failed: " + npe);
            }

            settings.setReplicationType("SYNCHRONOUS");  // This can be either ASYNCHRONOUS or SYNCHRONOUS
            try {
                settings.setTier(product.getProductSize()); 
            } catch (NullPointerException npe) {
                logger.error("setTier failed: " + npe);
            }

            BackupConfiguration element = settings.getBackupConfiguration();

            if (element == null) {
                element = new BackupConfiguration();
            } 

            element.setBinaryLogEnabled(true);
            element.setEnabled(true);

            settings.setBackupConfiguration(element);

            IpConfiguration ipConfiguration = settings.getIpConfiguration(); // comes back null
            if (null == ipConfiguration) {
                ipConfiguration = new IpConfiguration();
            }
            ipConfiguration.setIpv4Enabled(true);
            settings.setIpConfiguration(ipConfiguration);

            if (null != product.getProviderDataCenterId()) {
                LocationPreference locationPreference = settings.getLocationPreference();
                if (locationPreference == null) {
                    locationPreference = new LocationPreference();
                }

                locationPreference.setZone(product.getProviderDataCenterId());
                settings.setLocationPreference(locationPreference);
            }
            content.setSettings(settings);

            GoogleMethod method = new GoogleMethod(provider);
            Operation response = null;
            try {
                response = sqlAdmin.instances().insert(ctx.getAccountNumber(), content).execute();
            } catch (GoogleJsonResponseException ge) {
                if ((ge.getStatusMessage().equals("Conflict")) && (ge.getStatusCode() == 409)) {
                    throw new CloudException("The name " + dataSourceName + " has been used in the past 2 months. Once used, DB names are reserved for 2 months after their decomission.");
                } else {
                    throw new Exception(ge);
                }
            }
            try {
                method.getRDSOperationCompleteLong(ctx, response.getName());
            } catch (NullPointerException npe) {
                logger.error("getRDSOperationCompleteLong failed: " + npe);
                throw new CloudException("getRDSOperationCompleteLong failed: " + npe);
            }

            try {
                setPassword(dataSourceName, withAdminPassword);
            } catch (NullPointerException npe) {
                logger.error("setPassword failed: " + npe);
                throw new CloudException("setPassword failed: " + npe);
            }
        } catch (Exception e) {
            handleGoogleException(e);
            return null;
        }
        finally {
            APITrace.end();
        }
        return dataSourceName;
    }

    public void updateProductSize(@Nonnull String providerDatabaseId, @Nonnull String newProductSize) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        DatabaseInstance databaseInstance;
        try {
            databaseInstance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();

            if (null == databaseInstance) 
                throw new CloudException("Database instance " + providerDatabaseId + " does not exist.");

            Settings settings = databaseInstance.getSettings();
            settings.setTier(newProductSize.toUpperCase());
            databaseInstance.setSettings(settings);
            Operation response = sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, databaseInstance).execute();
            GoogleMethod method = new GoogleMethod(provider);
            method.getRDSOperationComplete(ctx, response.getName());
        } catch ( IOException e ) {
            handleGoogleException(e);
        }
    }

    /*
     * Notes: GCE does not allow mysql on custom ports.
     *        GCE does not allow clone to be created in different data center
     *        GCE does not allow databsae to be moved to different zones after creation
     */
    @Override
    public String createFromLatest(String dataSourceName, String providerDatabaseId, String productSize, String providerDataCenterId, int hostPort) throws InternalException, CloudException {
        APITrace.begin(provider, "RDBMS.createFromLatest");
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        try {
            InstancesCloneRequest content = new InstancesCloneRequest();
            CloneContext cloneContext = new CloneContext();
            cloneContext.setDestinationInstanceName(providerDatabaseId);
            content.setCloneContext(cloneContext);
            GoogleMethod method = new GoogleMethod(provider);
            //TODO: wait up to an hour
            Operation cloneResponse = sqlAdmin.instances().clone(ctx.getAccountNumber(), dataSourceName, content).execute(); // Seems to have a "Daily Limit Exceeded"
            method.getRDSOperationCompleteLong(ctx, cloneResponse.getName());

            updateProductSize(providerDatabaseId, productSize);
        } catch (Exception e) {
            System.out.println("createFromLatest cleanup 1");
            try {
                try {
                    Thread.sleep(10000);
                } catch(InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                // cleanup!
                removeDatabase(providerDatabaseId);
            } catch (Exception ex) { }
            handleGoogleException(e);
        } finally {
            APITrace.end();
        }
        return providerDatabaseId;
    }

    public Database getDatabase(String providerDatabaseId) throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.getDatabase");
        try {
            if( providerDatabaseId == null ) {
                return null;
            }
            Iterable<Database> dbs = listDatabases();
            if (dbs != null)
                for( Database database : dbs) 
                    if (database != null)
                        if( database.getProviderDatabaseId().equals(providerDatabaseId) ) 
                            return database;

            return null;
        } finally {
            APITrace.end();
        }
    }

    @Override
    public Iterable<DatabaseEngine> getDatabaseEngines() throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.getSupportedVersions");
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        ProviderContext ctx = provider.getContext();

        Collection<DatabaseEngine> cachedDatabaseEngines = (Collection<DatabaseEngine>)databaseEngines.get(ctx);
        if (cachedDatabaseEngines == null) {
            HashMap<DatabaseEngine, Boolean> engines = new HashMap<DatabaseEngine, Boolean>();
            try {
                FlagsListResponse flags = sqlAdmin.flags().list().execute();  // random 401 Unauthorized exception - Login Required... / 403 "Daily Limit Exceeded"
                for (Flag  flag : flags.getItems()) {
                    List<String> appliesTo = flag.getAppliesTo();
                    for (String dbNameVersion : appliesTo) {
                        String dbBaseName = dbNameVersion.replaceFirst("_.*", "");
                        engines.put(DatabaseEngine.valueOf(dbBaseName), true);
                    }
                }
                cachedDatabaseEngines = new ArrayList<DatabaseEngine>();
                cachedDatabaseEngines.addAll(engines.keySet());
                databaseEngines.put(ctx, cachedDatabaseEngines);
            } catch (Exception e) {
                handleGoogleException(e);
            } finally {
                APITrace.end();
            }
        }
        return cachedDatabaseEngines;
    }

    @Override
    public String getDefaultVersion(@Nonnull DatabaseEngine forEngine) throws CloudException, InternalException {
        if (forEngine == null)
            return null;
        APITrace.begin(provider, "RDBMS.getDefaultVersion");
        try {
            Iterable<String> versions = getSupportedVersions(forEngine);
            for (String version : versions)
                return version;  // just return first...
        }
        finally {
            APITrace.end();
        }
        return null;
    }

    @Override
    public @Nonnull Iterable<String> getSupportedVersions(@Nonnull DatabaseEngine forEngine) throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.getSupportedVersions");
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        HashMap<String, Boolean> versions = new HashMap<String, Boolean>();
        try {
            FlagsListResponse flags = sqlAdmin.flags().list().execute();
            for (Flag  flag : flags.getItems()) {
                List<String> appliesTo = flag.getAppliesTo();
                for (String dbNameVersion : appliesTo) 
                    versions.put(dbNameVersion.toLowerCase().replaceFirst(forEngine.toString().toLowerCase() + "_", "").replaceAll("_", "."), true);
            }
        }
        catch (Exception e) {
            handleGoogleException(e);
        }
        finally {
            APITrace.end();
        }
        return versions.keySet();
    }

    private static String readUrl(String urlString) throws Exception {
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuffer buffer = new StringBuffer();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1)
                buffer.append(chars, 0, read); 

            return buffer.toString();
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    @Override
    public @Nonnull Iterable<DatabaseProduct> listDatabaseProducts(@Nonnull DatabaseEngine forEngine) throws CloudException, InternalException {
        ArrayList<DatabaseProduct> products = new ArrayList<DatabaseProduct>();
        Iterable<DatabaseEngine> supportedEngines = this.getDatabaseEngines();

        APITrace.begin(provider, "RDBMS.listDatabaseProducts");

        boolean found = false;
        for (DatabaseEngine engine : supportedEngines)
            if (forEngine.equals(engine)) {
                found = true;
                break;
            }

        if (!found)
            return products;

        ProviderContext ctx = provider.getContext();
        Collection<JSONObject> cachedJsonPriceList = (Collection<JSONObject>)jsonPriceList.get(ctx);

        JSONObject json = null;
        if (cachedJsonPriceList != null)
            json = cachedJsonPriceList.iterator().next();
        else {
            String jsonString = null;
            try {
                jsonString = readUrl(jsonPriceUrl);
                jsonString = jsonString.replaceFirst("CP-APP-ENGINE-CLOUD-STORAGE", "CP-APP-ENGINE-CLOUD-STORAGE-DUP"); // fix a dup error
                jsonString = jsonString.replaceFirst("CP-APP-ENGINE-MEMCACHE", "CP-APP-ENGINE-MEMCACHE-DUP"); // fix a dup error
                json = new JSONObject(jsonString);
            } catch ( JSONException e ) {
                throw new InternalException("Problem obtaining pricing from " + jsonPriceUrl + " : " + e);
            } catch ( Exception e ) {
                throw new InternalException("Problem obtaining pricing from " + jsonPriceUrl + " : " + e);
            }
            Collection<JSONObject> jsonCachList = new ArrayList<JSONObject>();
            jsonCachList.add(json);
            jsonPriceList.put(ctx, jsonCachList);
        }

        Map<String, Float> hourly = new HashMap<String, Float>();
        Map<String, Float> daily = new HashMap<String, Float>();

        Float ioRate = null;
        Float storageRate = null;
        //Float trafficRate = null;

        try {
            //String version = (String) json.get("version");
            //String updated = (String) json.get("updated");
            //Double sustained_use_base = (Double) gcp_price_list.get("sustained_use_base");
            JSONObject gcp_price_list = (JSONObject) json.get("gcp_price_list");
            Iterator<?> keys = gcp_price_list.keys();
            while (keys.hasNext()){
                String k = (String) keys.next();
                if (k.contains("CLOUDSQL")) {
                    String[] components = k.split("-");
                    JSONObject val = (JSONObject) gcp_price_list.get(k);
                    Float price = null;

                    String regionId = ctx.getRegionId();
                    price = new Float((Double) val.get("us"));
                    try {
                        if (regionId.startsWith("europe"))
                            price = new Float((Double) val.get("eu"));
                        else if (regionId.startsWith("asia"))
                            price = new Float((Double) val.get("apac"));
                    } catch (JSONException e) {
                        // ignore and just use US price.
                    }
                    if (components[2].equals("PERUSE"))
                        hourly.put(components[3], price);
                    else if (components[2].equals("PACKAGE"))
                        daily.put(components[3], price);
                    else if (components[2].equals("IO"))
                        ioRate = price;         // CP-CLOUDSQL-IO - us = 0.1 per million I/O's
                    else if (components[2].equals("STORAGE"))
                        storageRate = price;    // CP-CLOUDSQL-STORAGE - us = 0.24 per month per GB
                    //else if (components[2].equals("TRAFFIC"))
                    //    trafficRate = price;    // CP-CLOUDSQL-TRAFFIC - us = 0.12 per GB (outbound only)
                }
            }
        } catch ( Exception e ) {
            throw new InternalException("Problem obtaining pricing from " + jsonPriceUrl);
        }

        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        Map<String, Float> hourlyRate = Collections.unmodifiableMap(hourly);
        Map<String, Float> dailyRate = Collections.unmodifiableMap(daily);

        Collection<Tier> tierList = (Collection<Tier>)tiersList.get(ctx);
        List<Tier> tiers = null;
        if (tierList != null)
            tiers = (List<Tier>) tierList;
        else {
            try {
                TiersListResponse tierListResponse = sqlAdmin.tiers().list(ctx.getAccountNumber()).execute();  // 401 unauthorized. 7 min run time
                tiers = tierListResponse.getItems();
                tiersList.put(ctx, tiers);
            } catch( Exception e ) {
                handleGoogleException(e);
            }
        }
        try {
            DatabaseProduct product = null;
            for (Tier t : tiers) {
                int sizeInGB = (int) ( t.getDiskQuota() / gigabyte );
                int ramInMB = (int) ( t.getRAM() / megabyte );

                // Hourly rate
                product = new DatabaseProduct(t.getTier(), "PERUSE " + t.getTier() + " - " + ramInMB + "MB RAM Hourly");
                product.setLicenseModel(DatabaseLicenseModel.GENERAL_PUBLIC_LICENSE);
                product.setEngine(forEngine);
                product.setStorageInGigabytes(sizeInGB);
                product.setCurrency("USD");
                product.setStandardHourlyRate(hourlyRate.get(t.getTier()));
                product.setStandardIoRate(ioRate);
                product.setStandardStorageRate(storageRate);
                products.add(product);

                // Daily rate
                product = new DatabaseProduct(t.getTier(), "PACKAGE " + t.getTier() + " - " + ramInMB + "MB RAM Daily");
                product.setEngine(forEngine);
                product.setStorageInGigabytes(sizeInGB);
                product.setCurrency("USD");
                product.setStandardHourlyRate(dailyRate.get(t.getTier()) / 24.0f);
                product.setStandardIoRate(ioRate);
                product.setStandardStorageRate(storageRate);
                product.setHighAvailability(true);       // Always On
                products.add(product);
            }
        } finally {
            APITrace.end();
        }
        return products; 
    }

    @Override
    public DatabaseSnapshot getSnapshot(String providerDbSnapshotId) throws CloudException, InternalException {
        throw new CloudException("GCE Cloud SQL does not support database snapshots.");
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        try {
            getSupportedVersions(DatabaseEngine.MYSQL);
            //listDatabases();  // expensive call, but with caching not too bad. hope they dont beat on it.
            return true;
        } catch (Exception e) {
            // ignore. just means we are not subscribed!
        }
        return false;
    }

    @Override
    public Iterable<String> listAccess(@Nonnull String toProviderDatabaseId) throws CloudException, InternalException {
        List<String> dbAccess = new ArrayList<String>();
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        Collection<DatabaseInstance> databaseInstances = null;

        try {
            InstancesListResponse databases = sqlAdmin.instances().list(ctx.getAccountNumber()).execute();
            databaseInstances = databases.getItems();

        } catch (Exception e) {
            handleGoogleException(e);
        }

        for (DatabaseInstance db : databaseInstances) {
            List<AclEntry> networks = db.getSettings().getIpConfiguration().getAuthorizedNetworks();
            if (networks != null) {
                for (AclEntry network : networks) {
                    dbAccess.add(network.getValue());
                }
            }
        }

        return dbAccess;
    }

    @Override
    public Iterable<DatabaseConfiguration> listConfigurations() throws CloudException, InternalException {
        throw new CloudException("GCE Cloud SQL does not support database backup configurations.");
    }

    @Override
    public DatabaseConfiguration getConfiguration(String providerConfigurationId) throws CloudException, InternalException {
        throw new CloudException("GCE Cloud SQL does not support database backup configurations.");
    }

    @Override
    public Iterable<ResourceStatus> listDatabaseStatus() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        ArrayList<ResourceStatus> list = new ArrayList<ResourceStatus>();

        Collection<DatabaseInstance> databaseInstances = (Collection<DatabaseInstance>)listDatabasesInstanceCache.get(ctx);

        if (null == databaseInstances) {
            try {
                InstancesListResponse databases = sqlAdmin.instances().list(ctx.getAccountNumber()).execute();
                databaseInstances = databases.getItems();
                listDatabasesInstanceCache.put(ctx, databaseInstances);
            } catch (Exception e) {
                handleGoogleException(e);
            }
        }

        for (DatabaseInstance instance : databaseInstances) {
            ResourceStatus status = new ResourceStatus(instance.getName(), instance.getState()); // .getInstance()
            list.add(status);
        }

        return list;
    }

    @Override
    public Iterable<Database> listDatabases() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();


        Collection<Database> list = (Collection<Database>)listDatabasesCache.get(ctx);
        if (null == list) {
            Collection<DatabaseInstance> databaseInstances = (Collection<DatabaseInstance>)listDatabasesInstanceCache.get(ctx);
            if (null == databaseInstances) {
                try {
                    InstancesListResponse databases = sqlAdmin.instances().list(ctx.getAccountNumber()).execute();
                    if (null != databases)
                        databaseInstances = databases.getItems();
                    listDatabasesInstanceCache.put(ctx, databaseInstances);
                } catch (Exception e) {
                    handleGoogleException(e);
                }
            }

            try {
                list = new ArrayList<Database>();
                if (null != databaseInstances) {
                    for (DatabaseInstance d : databaseInstances) {
                        Settings s = d.getSettings();
                        if (null == s)
                            throw new CloudException("getSettings() returned null!");
                        BackupConfiguration backupConfig = s.getBackupConfiguration();

                        Database database = new Database();
                        database.setAdminUser("root");
                        database.setAllocatedStorageInGb((int)(d.getMaxDiskSize() / gigabyte));
                        if (null == backupConfig) {
                            backupConfig = new BackupConfiguration();
                        }
                        database.setConfiguration("unknown-1");

                        OperationsListResponse operations = sqlAdmin.operations().list(d.getProject(), d.getName()).execute(); // .getInstance()
                        for (Operation operation: operations.getItems())
                            if ((operation.getOperationType().equals("CREATE")) && (operation.getEndTime() != null))
                                database.setCreationTimestamp(operation.getEndTime().getValue());

                        String googleDBState = d.getState();
                        if (googleDBState.equals("RUNNABLE")) {
                            database.setCurrentState(DatabaseState.AVAILABLE);
                        } else if (googleDBState.equals("SUSPENDED")) {
                            database.setCurrentState(DatabaseState.SUSPENDED);
                        } else if (googleDBState.equals("PENDING_CREATE")) {
                            database.setCurrentState(DatabaseState.PENDING);
                        } else if (googleDBState.equals("MAINTENANCE")) {
                            database.setCurrentState(DatabaseState.MAINTENANCE);
                        } else if (googleDBState.equals("UNKNOWN_STATE")) {
                            database.setCurrentState(DatabaseState.UNKNOWN);
                        }

                        if ((d.getDatabaseVersion().equals("MYSQL_5_5")) || (d.getDatabaseVersion().equals("MYSQL_5_6")))
                            database.setEngine(DatabaseEngine.MYSQL); 

                        database.setHighAvailability(true);  // GCE is always high availability
                        database.setHostPort(3306);          // Default mysql port

                        if ((null != d) && (null != d.getIpAddresses()) && (null != d.getIpAddresses().get(0)))
                            database.setHostName(d.getIpAddresses().get(0).getIpAddress());

                        if ((null != backupConfig) && (backupConfig.getStartTime() != null)) {  // Start time for the daily backup configuration in UTC timezone in the 24 hour format - HH:MM.
                            String[] backupWindowStartTimeComponents = backupConfig.getStartTime().split(":");
                            if ((null != backupWindowStartTimeComponents) 
                                && (null != backupWindowStartTimeComponents[0]) 
                                && (null != backupWindowStartTimeComponents[1])) {
                                int startHour = Integer.parseInt(backupWindowStartTimeComponents[0]);
                                TimeWindow backupTimeWindow = new TimeWindow();
                                backupTimeWindow.setStartHour(startHour);
                                backupTimeWindow.setStartMinute(Integer.parseInt(backupWindowStartTimeComponents[1]));
                                backupTimeWindow.setEndHour((startHour + 4) % 24);
                                backupTimeWindow.setEndMinute(Integer.parseInt(backupWindowStartTimeComponents[1]));
                                backupTimeWindow.setStartDayOfWeek(DayOfWeek.MONDAY);
                                backupTimeWindow.setEndDayOfWeek(DayOfWeek.SUNDAY);
                                database.setBackupWindow(backupTimeWindow);
                                database.setMaintenanceWindow(backupTimeWindow);    // I think the maintenance window is same as backup window.
                            }
                        }

                        database.setName(d.getName());
                        database.setProductSize(s.getTier());
                        database.setProviderDatabaseId(d.getName());
                        database.setProviderOwnerId(d.getProject());
                        String regionId = d.getRegion();
                        if (regionId.equals("us-central")) {
                            regionId = "us-central1";  // fix for google inconsistency 
                        }
                        database.setProviderRegionId(regionId);
                        if ((null != d) 
                            && (null != d.getSettings()) 
                            && (null != d.getSettings().getLocationPreference())) {
                            database.setProviderDataCenterId(d.getSettings().getLocationPreference().getZone());
                        }

                        if (d.getDatabaseVersion().startsWith("MYSQL_")) {
                            database.setEngineVersion(d.getDatabaseVersion().replace("MYSQL_", "").replace("_", "."));
                        }
                        //backupConfigItem.getBinaryLogEnabled()
                        //database.setRecoveryPointTimestamp(recoveryPointTimestamp);
                        //database.setSnapshotWindow(snapshotWindow);
                        //database.setSnapshotRetentionInDays(snapshotRetentionInDays);
                        //d.getServerCaCert();
                        //s.getAuthorizedGaeApplications();
                        if( d.getDatabaseVersion() != null ) {
                            database.setEngineVersion(d.getDatabaseVersion().trim().toLowerCase());
                        }
                        list.add(database);
                    }
                    listDatabasesCache.put(ctx, list);
                }
            } catch (Exception e) {
                handleGoogleException(e);
            }
        }
        return list;
    }

    @Override
    public Collection<ConfigurationParameter> listParameters(String forProviderConfigurationId) throws CloudException, InternalException {
        throw new CloudException("GCE Cloud SQL does not support database backup configurations.");
    }

    @Override
    public void removeConfiguration(String providerConfigurationId) throws CloudException, InternalException {
        throw new CloudException("GCE Cloud SQL does not support database backup configurations.");
    }

    @Override
    public void removeDatabase(String providerDatabaseId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        try {
            Operation response = sqlAdmin.instances().delete(ctx.getAccountNumber(), providerDatabaseId).execute();
        } catch ( IOException e ) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                if ((gjre.getStatusMessage().equals("Conflict")) && (gjre.getStatusCode() == 409)) {
                    throw new CloudException("Database already deleted.");
                } else {
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                }
            } else
                throw new CloudException(e);
        } catch (Exception e) {
            throw new CloudException(e);
        }

        Collection<Database> list = (Collection<Database>)listDatabasesCache.get(ctx);
        if (null != list) {
            for (Database db : list) {
                if (db.getName().equals(providerDatabaseId)) {
                    list.remove(db);  // remove it from the cache
                    listDatabasesCache.put(ctx, list);
                    break;
                }
            }
        }

    }

    @Override
    public void resetConfiguration(String providerConfigurationId, String... parameters) throws CloudException, InternalException {
        throw new CloudException("GCE Cloud SQL does not support database backup configurations.");
    }

    @Override
    public void restart(String providerDatabaseId, boolean blockUntilDone) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        try {
            GoogleMethod method = new GoogleMethod(provider);
            Operation response = sqlAdmin.instances().restart(ctx.getAccountNumber(), providerDatabaseId).execute();
            method.getRDSOperationComplete(ctx, response.getName());
        } catch ( IOException e ) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception e) {
            throw new CloudException(e);
        }
    }

    @Override
    public void updateConfiguration(String providerConfigurationId, ConfigurationParameter... parameters) throws CloudException, InternalException {
        throw new CloudException("GCE Cloud SQL does not support database backup configurations.");
    }

    @Override
    public DatabaseSnapshot snapshot(String providerDatabaseId, String name) throws CloudException, InternalException {
        throw new InternalException("Take snapshot not supported");
    }

    @Override
    public void removeSnapshot(String providerSnapshotId) throws CloudException, InternalException {
        throw new CloudException("Remove snapshot not supported");
    }

    @Override
    public Iterable<DatabaseSnapshot> listSnapshots(String forOptionalProviderDatabaseId) throws CloudException, InternalException {
        ArrayList<DatabaseSnapshot> snapshots = new ArrayList<DatabaseSnapshot>();
        return snapshots;
        // throw new CloudException("List snapshot not supported");
    }

    @Override
    public String createFromSnapshot(String dataSourceName, String providerDatabaseId, String providerDbSnapshotId, String productSize, String providerDataCenterId, int hostPort) throws CloudException, InternalException {
        return null;
    }

    @Override
    public DatabaseBackup getUsableBackup(String providerDbId, String beforeTimestamp) throws CloudException, InternalException {
        Date testStartTime = null, 
             beforeStartTime = null,
             bestCandidateStartTime = null;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        DatabaseBackup bestCandidateBackup = null;
        try {
            beforeStartTime = formatter.parse(beforeTimestamp);
        } catch ( ParseException e ) {
            throw new InternalException("Invalid beforeTimestamp passed to getUsableBackup");
        }
        Iterable<DatabaseBackup> backupList = listBackups(providerDbId);
        for (DatabaseBackup backup : backupList) 
            if (DatabaseBackupState.AVAILABLE == backup.getCurrentState()) {
                try {
                    testStartTime = formatter.parse(backup.getStartTime());
                    if ((testStartTime.before(beforeStartTime)) && 
                        ((bestCandidateStartTime == null) || (testStartTime.after(bestCandidateStartTime)))){
                        bestCandidateBackup = backup;
                        bestCandidateStartTime = testStartTime;
                    }
                } catch ( ParseException e ) {}
            }
        if (bestCandidateBackup != null)
            return bestCandidateBackup;
        else
            throw new CloudException("No available backups meet requirements.");
    }

    @Override
    public Iterable<DatabaseBackup> listBackups( String forOptionalProviderDatabaseId ) throws CloudException, InternalException {
        ArrayList<DatabaseBackup> backups = new ArrayList<DatabaseBackup>();
        if (forOptionalProviderDatabaseId == null) {
            Iterable<Database> dataBases = listDatabases();
            for (Database db : dataBases) 
                backups.addAll(getBackupForDatabase(db.getProviderDatabaseId()));
        } else 
            backups = getBackupForDatabase(forOptionalProviderDatabaseId);

        return backups;
    }

    @Override
    public RelationalDatabaseCapabilities getCapabilities() throws InternalException, CloudException {
        return new GCERelationalDatabaseCapabilities(provider);
    }

    @Override
    public void removeBackup(DatabaseBackup backup) throws CloudException, InternalException {
        throw new CloudException("GCE Cloud SQL does not support deleting specific database backups.");
    }

    public ArrayList<DatabaseBackup> getBackupForDatabase(String forDatabaseId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        ArrayList<DatabaseBackup> backups = new ArrayList<DatabaseBackup>();

        Database db = getDatabase(forDatabaseId);

        BackupRunsListResponse backupRuns = null;
        try {
            backupRuns = sqlAdmin.backupRuns().list(ctx.getAccountNumber(), forDatabaseId).execute();
        } catch (Exception e) {
            handleGoogleException(e);
        }
        try {
            if (null != backupRuns.getItems())
                for (BackupRun backupItem : backupRuns.getItems()) {
                    DatabaseBackup backup = new DatabaseBackup();
                    String instance = backupItem.getInstance();

                    backup.setProviderDatabaseId(instance);
                    backup.setAdminUser(db.getAdminUser());
                    backup.setProviderOwnerId(db.getProviderOwnerId());
                    String regionId = db.getProviderRegionId();
                    if (regionId.equals("us-central")) {
                        regionId = "us-central1"; // fix for google inconsistency 
                    }
                    backup.setProviderRegionId(regionId);

                    backup.setBackupConfiguration(backupItem.getInstance());
                    backup.setEnqueuedTime(backupItem.getEnqueuedTime().toString());

                    String status = backupItem.getStatus();
                    if (status.equals("SUCCESSFUL")) {
                        backup.setCurrentState(DatabaseBackupState.AVAILABLE);
                        backup.setStartTime(backupItem.getStartTime().toString());
                        backup.setEndTime(backupItem.getEndTime().toString());
                    } else {
                        backup.setCurrentState(DatabaseBackupState.valueOf(status)); 
                        // this will likely barf first time it gets caught mid backup, 
                        // but with backup windows being 4 hours... will have to wait to catch this one...
                    }

                    backup.setProviderBackupId(backupItem.getId().toString());
                    OperationError error = backupItem.getError(); // null
                    if (error != null) 
                        backup.setCurrentState(DatabaseBackupState.ERROR);

                    // works like list, but for just one backup
                    //BackupRun unknownResult = sqlAdmin.backupRuns().get(ctx.getAccountNumber(), forDatabaseId, backupItem.getBackupConfiguration(), backupItem.getDueTime().toString()).execute();
                    //{"backupConfiguration":"4be91d6f-3ab7-4a21-b082-fad698a16cb0","dueTime":"2014-10-22T10:00:00.096Z","enqueuedTime":"2014-10-22T13:12:16.882Z","instance":"stateless-test-database","kind":"sql#backupRun","status":"SKIPPED"}

                    // db.isHighAvailability();
                    // Unknown what to do with
                    //String config = backup.getBackupConfiguration(); // 991a6ae6-17c7-48a1-8410-9807b8e3e2ad
                    //Map<String, Object> keys = backup.getUnknownKeys();
                    //int retentionDays = db.getSnapshotRetentionInDays();
                    //String kind = backup.getKind(); // sql#backupRun
                    //snapShot.setStorageInGigabytes(storageInGigabytes);  // N.A.

                    backups.add(backup);
                }
        } catch (Exception e) {
            handleGoogleException(e);
        }

        return backups;
    }

    @Override
    public void restoreBackup(DatabaseBackup backup) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            String acct = ctx.getAccountNumber();

            InstancesRestoreBackupRequest request = new InstancesRestoreBackupRequest();

            RestoreBackupContext restoreBackupContext = new RestoreBackupContext();

            restoreBackupContext.setBackupRunId(new Long(backup.getProviderBackupId()));

            request.setRestoreBackupContext(restoreBackupContext);

            Operation response = sqlAdmin.instances().restoreBackup(acct, backup.getProviderDatabaseId(), request).execute();
            method.getRDSOperationComplete(ctx, response.getName());
        } catch (Exception e) {
            handleGoogleException(e);
        }

        /* project Project ID of the project that contains the instance.
         * instance Cloud SQL instance ID. This does not include the project ID.
         * backupConfiguration The identifier of the backup configuration. This gets generated automatically when a backup configuration is created.
         * dueTime The time when this run is due to start in RFC 3339 format, for example 2012-11-15T16:19:00.094Z.
         */
    }

    @Deprecated
    public Iterable<DatabaseProduct> getDatabaseProducts( DatabaseEngine forEngine ) throws CloudException, InternalException {
        return listDatabaseProducts(forEngine);
    }

    @Deprecated
    public boolean isSupportsFirewallRules() {
        boolean supportsFirewallRules = false;
        try {
            supportsFirewallRules = getCapabilities().isSupportsFirewallRules();
        } catch( Exception e ) {  } // ignore

        return supportsFirewallRules;
    }

    @Deprecated
    public boolean isSupportsHighAvailability() throws CloudException, InternalException {
        // https://cloud.google.com/developers/articles/building-high-availability-applications-on-google-compute-engine
        /*
         * Database db = getDatabase(forDatabaseId);
         * db.isHighAvailability()
         */
        return true;
    }

    @Deprecated
    public boolean isSupportsLowAvailability() throws CloudException, InternalException {
        boolean supportsLowAvailability = false;
        try {
            supportsLowAvailability = getCapabilities().isSupportsLowAvailability();
        } catch( Exception e ) {  } // ignore

        return supportsLowAvailability;
    }

    @Deprecated
    public boolean isSupportsMaintenanceWindows() {
        boolean supportsMaintenanceWindows = false;
        try {
            supportsMaintenanceWindows = getCapabilities().isSupportsMaintenanceWindows();
        } catch( Exception e ) {  } // ignore

        return supportsMaintenanceWindows;
    }

    @Deprecated
    public boolean isSupportsSnapshots() {
        /*
         * Google Cloud SQL backups are taken by using FLUSH TABLES WITH READ LOCK to create a snapshot. 
         * This will prevent writes, typically for a few seconds. Even though the instance remains online, 
         * and reads are unaffected, it is recommended to schedule backups during the quietest period for 
         * your instance. If there is a pending operation at the time of the backup attempt, Google Cloud 
         * SQL retries until the backup window is over. Operations that block backup are long-running 
         * operations such as import, export, update (e.g., for an instance metadata change), and 
         * restart (e.g., for an instance restart).
         */
        boolean supportsSnapshots = false;
        try {
            supportsSnapshots = getCapabilities().isSupportsSnapshots();
        } catch( Exception e ) {  } // ignore

        return supportsSnapshots;
    }

    @Deprecated
    public String getProviderTermForDatabase(Locale locale) {
        String providerTermForDatabase = null;
        try {
            providerTermForDatabase = getCapabilities().getProviderTermForDatabase(locale);
        } catch( Exception e ) {  } // ignore

        return providerTermForDatabase;
    }

    @Deprecated
    public String getProviderTermForSnapshot(Locale locale) {
        String providerTermForSnapshot = null;
        try {
            providerTermForSnapshot = getCapabilities().getProviderTermForSnapshot(locale);
        } catch( Exception e ) {  } // ignore

        return providerTermForSnapshot;
    }
}

