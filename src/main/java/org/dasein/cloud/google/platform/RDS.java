package org.dasein.cloud.google.platform;

//import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.TimeWindow;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.capabilities.GCERelationalDatabaseCapabilities;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.ConfigurationParameter;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseBackup;
import org.dasein.cloud.platform.DatabaseBackupState;
import org.dasein.cloud.platform.DatabaseConfiguration;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.DatabaseSnapshot;
import org.dasein.cloud.platform.DatabaseState;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;
import org.dasein.cloud.platform.RelationalDatabaseSupport;
import org.dasein.cloud.util.APITrace;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.BackupConfiguration;
import com.google.api.services.sqladmin.model.BackupRun;
import com.google.api.services.sqladmin.model.BackupRunsListResponse;
import com.google.api.services.sqladmin.model.DatabaseFlags;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.Flag;
import com.google.api.services.sqladmin.model.FlagsListResponse;
import com.google.api.services.sqladmin.model.InstancesDeleteResponse;
import com.google.api.services.sqladmin.model.InstancesInsertResponse;
import com.google.api.services.sqladmin.model.InstancesListResponse;
import com.google.api.services.sqladmin.model.InstancesRestartResponse;
import com.google.api.services.sqladmin.model.InstancesRestoreBackupResponse;
import com.google.api.services.sqladmin.model.InstancesUpdateResponse;
import com.google.api.services.sqladmin.model.LocationPreference;
import com.google.api.services.sqladmin.model.OperationError;
import com.google.api.services.sqladmin.model.Settings;
import com.google.api.services.sqladmin.model.Tier;
import com.google.api.services.sqladmin.model.TiersListResponse;

/*
 * https://developers.google.com/cloud-sql/faq#data_location
 */

public class RDS implements RelationalDatabaseSupport {
    static private volatile ArrayList<DatabaseEngine> engines = null;
    private volatile ArrayList<DatabaseProduct> databaseProducts = null;
    private Google provider;

	public RDS(Google provider) {
        this.provider = provider;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
	    // TODO: implement me
	    return new String[0];
	}

	@Override
	public void alterDatabase(String providerDatabaseId,
			boolean applyImmediately, String productSize,
			int storageInGigabytes, String configurationId,
			String newAdminUser, String newAdminPassword, int newPort,
			int snapshotRetentionInDays, TimeWindow preferredMaintenanceWindow,
			TimeWindow preferredBackupWindow) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		
	}

    @Override
    public String createFromScratch(String dataSourceName, DatabaseProduct product, String databaseVersion, String withAdminUser, String withAdminPassword, int hostPort) throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.createFromScratch");
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        try {
            DatabaseInstance content = new DatabaseInstance();
            String newDatabaseVersion = getDefaultVersion(product.getEngine()).replaceAll("\\.", "_");

            content.setInstance(dataSourceName);
            content.setDatabaseVersion(product.getEngine().name() + "_" + newDatabaseVersion);
            //content.setKind("sql#instance"); // REDUNDANT?
            content.setProject(ctx.getAccountNumber());
            content.setRegion(ctx.getRegionId().replaceFirst("[0-9]$", ""));  // Oddly setRegion needs just the base, no number after the region...
            // THINGS WE HAVE AND HAVE NOT USED
            // withAdminUser
            // withAdminPassword  // SQLAdmin.Instances.SetRootPassword 
            // hostPort
            // THINGS IT HAS AND DONT KNOW
            //content.setCurrentDiskSize(currentDiskSize);				// long
            //content.setMaxDiskSize(maxDiskSize);						// long
            //java.util.List<IpMapping> ipAddresses = new ArrayList<IpMapping>();
            //ipAddresses.add(new IpMapping().setIpAddress(ipAddress));	// String
            //content.setIpAddresses(ipAddresses);
            //SslCert serverCaCert = null;
            //content.setServerCaCert(serverCaCert );

            Settings settings = new Settings();
            settings.setActivationPolicy("ALWAYS");  // ALWAYS NEVER ON_DEMAND

            //java.util.List<BackupConfiguration> backupConfiguration;
            //BackupConfiguration element;
            //element.set(fieldName, value);
            //element.setBinaryLogEnabled(binaryLogEnabled);
            //element.setEnabled(enabled);
            //element.setId(id);
            //element.setStartTime(startTime);
            //backupConfiguration.set(0, element);
            //settings.setBackupConfiguration(backupConfiguration);

            //java.util.List<DatabaseFlags> databaseFlags;

            //DatabaseFlags element;
            //element.setName("name").setValue("value");
            // The name of the flag. These flags are passed at instance startup, so include both MySQL server options and MySQL system variables. Flags should be specified with underscores, not hyphens. Refer to the official MySQL documentation on server options and system variables for descriptions of what these flags do. Acceptable values are: event_scheduler on or off (Note: The event scheduler will only work reliably if the instance activationPolicy is set to ALWAYS.) general_log on or off group_concat_max_len 4..17179869184 innodb_flush_log_at_trx_commit 0..2 innodb_lock_wait_timeout 1..1073741824 log_bin_trust_function_creators on or off log_output Can be either TABLE or NONE, FILE is not supported. log_queries_not_using_indexes on or off long_query_time 0..30000000 lower_case_table_names 0..2 max_allowed_packet 16384..1073741824 read_only on or off skip_show_database on or off slow_query_log on or off wait_timeout 1..31536000
            //databaseFlags.set(0, element);
            //settings.setDatabaseFlags(databaseFlags);

            //IpConfiguration ipConfiguration;
            //ipConfiguration.setAuthorizedNetworks(authorizedNetworks);
            //ipConfiguration.setRequireSsl(requireSsl);
            //settings.setIpConfiguration(ipConfiguration);

            // settings.setKind("sql#settings"); // REDUNDANT?

            //LocationPreference locationPreference;
            //locationPreference.setZone(zone); //us-centra1-a, us-central1-b
            //settings.setLocationPreference(locationPreference);

            settings.setPricingPlan("PER_USE"); // This can be either PER_USE or PACKAGE
            settings.setReplicationType("SYNCHRONOUS");  // This can be either ASYNCHRONOUS or SYNCHRONOUS
            settings.setTier("D0"); // D0 D1 D2 D4 D8 D16 D32

            content.setSettings(settings);

            GoogleMethod method = new GoogleMethod(provider);
            InstancesInsertResponse response = sqlAdmin.instances().insert(ctx.getAccountNumber(), content).execute();

            if (method.getRDSOperationComplete(ctx, response.getOperation(), dataSourceName))
                return dataSourceName;
            else
                return null; // Should never reach here. should get an exception from getRDSOperationComplete
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new org.dasein.cloud.google.GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception e) {
            throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public String createFromLatest(String dataSourceName, String providerDatabaseId, String productSize, String providerDataCenterId, int hostPort) throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String createFromTimestamp(String dataSourceName, String providerDatabaseId, long beforeTimestamp, String productSize, String providerDataCenterId, int hostPort) throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DatabaseConfiguration getConfiguration(String providerConfigurationId) throws CloudException, InternalException {
	    ProviderContext ctx = provider.getContext();
	    SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();



	    // TODO Auto-generated method stub
	    return null;
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
        } catch (Exception e) {
        	throw new CloudException(e);
        }

        finally {
            APITrace.end();
        }
    }

	@Override
	public Iterable<DatabaseEngine> getDatabaseEngines() throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.getSupportedVersions");
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        HashMap<DatabaseEngine, Boolean> engines = new HashMap<DatabaseEngine, Boolean>();
        try {
            FlagsListResponse flags = sqlAdmin.flags().list().execute();
            for (Flag  flag : flags.getItems()) {
                List<String> appliesTo = flag.getAppliesTo();
                for (String dbNameVersion : appliesTo) {
                    String dbBaseName = dbNameVersion.replaceFirst("_.*", "");
                    engines.put(DatabaseEngine.valueOf(dbBaseName), true);
                }
            }
        }
        catch( IOException e ) {
            throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
        return engines.keySet();
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
        catch( IOException e ) {
            throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
        return versions.keySet();
	}


	
	@Override
	public @Nonnull Iterable<DatabaseProduct> listDatabaseProducts(@Nonnull DatabaseEngine forEngine) throws CloudException, InternalException {
	    APITrace.begin(provider, "RDBMS.listDatabaseProducts");
	    ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        ArrayList<DatabaseProduct> products = new ArrayList<DatabaseProduct>();

        try {
            TiersListResponse tierList = sqlAdmin.tiers().list(ctx.getAccountNumber()).execute();
            List<Tier> tiers = tierList.getItems();
            float fakeRate = 0.01f;
            for (Tier t : tiers) {
                DatabaseProduct product = null;
                int ramInMB = (int) ( t.getRAM() /  1048576 );
                product = new DatabaseProduct(t.getTier(), ramInMB + "MB RAM");
                product.setEngine(forEngine);
                // TODO  Which to use? 1 GB = 1000000000 bytes or 1 GiB = 1073741824 bytes 
                int sizeInGB = (int) ( t.getDiskQuota() / 1073741824 );
                product.setStorageInGigabytes(sizeInGB);

                product.setStandardHourlyRate(fakeRate);    // unknown as yet
                product.setStandardIoRate(fakeRate);        // unknown as yet
                product.setStandardStorageRate(fakeRate);   // unknown as yet
                fakeRate += 0.01f;
                product.setHighAvailability(false);       // unknown as yet
                /*
                 * Database db = getDatabase(forDatabaseId);
                 * db.isHighAvailability()
                 */
                //t.getRegion(); // list of regions

                products.add(product);
            }
        }
        catch( Exception e ) {
            throw new CloudException(e);
        }
		finally {
            APITrace.end();
        }
		return products; 
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

	@Override
	public DatabaseSnapshot getSnapshot(String providerDbSnapshotId) throws CloudException, InternalException {
		return null;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		// TODO Verify that i interpreted this correctly.
		return true;
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
            List<String> authorizedNetworks = settings.getIpConfiguration().getAuthorizedNetworks();
            if (authorizedNetworks == null)
                authorizedNetworks = new ArrayList<String>();
            authorizedNetworks.add(sourceCidr);
            settings.getIpConfiguration().setAuthorizedNetworks(authorizedNetworks);
            GoogleMethod method = new GoogleMethod(provider);
            InstancesUpdateResponse response = sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, instance).execute();
            boolean result = method.getRDSOperationComplete(ctx, response.getOperation(), providerDatabaseId);
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
                InstancesUpdateResponse response = sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, instance).execute();
                boolean result = method.getRDSOperationComplete(ctx, response.getOperation(), providerDatabaseId);


            }
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
    public void revokeAccess(String providerDatabaseId, String sourceCidr) throws CloudException, InternalException {
        if (sourceCidr.matches("[0-9][0-9./]*[0-9]")) {
            revokeAccessAuthorizedNetworks(providerDatabaseId, sourceCidr);
        } else
            revokeAccessAuthorizedGaeApplications(providerDatabaseId, sourceCidr);
    }

    private void revokeAccessAuthorizedGaeApplications(String providerDatabaseId, String deauthedApplication) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        try {
            DatabaseInstance instance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();
            if (instance != null) {
                Settings settings = instance.getSettings();
                List<String> authorizedApplications = settings.getAuthorizedGaeApplications();
                if (authorizedApplications == null) 
                    authorizedApplications = new ArrayList<String>();
                authorizedApplications.remove(deauthedApplication);
                settings.setAuthorizedGaeApplications(authorizedApplications);
                instance.setSettings(settings);

                GoogleMethod method = new GoogleMethod(provider);
                InstancesUpdateResponse response = sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, instance).execute();
                boolean result = method.getRDSOperationComplete(ctx, response.getOperation(), providerDatabaseId);
            }
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

    private void revokeAccessAuthorizedNetworks(String providerDatabaseId, String deauthedCidr)  throws CloudException, InternalException{
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        try {
            DatabaseInstance instance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();
            Settings settings = instance.getSettings();
            List<String> authorizedNetworks = settings.getIpConfiguration().getAuthorizedNetworks();
            if (authorizedNetworks == null)
                authorizedNetworks = new ArrayList<String>();
            authorizedNetworks.remove(deauthedCidr);
            settings.getIpConfiguration().setAuthorizedNetworks(authorizedNetworks);
            GoogleMethod method = new GoogleMethod(provider);
            InstancesUpdateResponse response = sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, instance).execute();
            boolean result = method.getRDSOperationComplete(ctx, response.getOperation(), providerDatabaseId);
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
    public Iterable<String> listAccess(String toProviderDatabaseId) throws CloudException, InternalException {
        List<String> dbAccess = new ArrayList<String>();
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        try {
            InstancesListResponse databases = sqlAdmin.instances().list(ctx.getAccountNumber()).execute();
            for (DatabaseInstance db : databases.getItems()) {
                if (toProviderDatabaseId.equals(db.getInstance())) {
                    List<String> tmpDbAccess = db.getSettings().getAuthorizedGaeApplications();
                    if (tmpDbAccess != null)
                        dbAccess = tmpDbAccess;
                }
                List<String> networks = db.getSettings().getIpConfiguration().getAuthorizedNetworks();
                if (networks != null)
                    dbAccess.addAll(networks);
            }
        } catch ( IOException e ) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }

        return dbAccess;
    }

	@Override
	public Iterable<DatabaseConfiguration> listConfigurations() throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

    @Override
    public Iterable<ResourceStatus> listDatabaseStatus() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        ArrayList<ResourceStatus> list = new ArrayList<ResourceStatus>();
        java.util.List<DatabaseInstance> dbInstances = null;
        try {
            InstancesListResponse response = sqlAdmin.instances().list(ctx.getAccountNumber()).execute();
            if ((response != null) && (!response.isEmpty()) && (response.getItems() != null))
                dbInstances = response.getItems();      // null exception here?
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
        }

        for (DatabaseInstance instance : dbInstances) {
            ResourceStatus status = new ResourceStatus(instance.getInstance(), instance.getState());
            list.add(status);
        }

        return list;
    }

	@Override
    public Iterable<Database> listDatabases() throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();
		SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

		ArrayList<Database> list = new ArrayList<Database>();
		java.util.List<DatabaseInstance> resp = null;
		try {
			resp = sqlAdmin.instances().list(ctx.getAccountNumber()).execute().getItems();  // null exception here...
		} catch (IOException e) {
			if (e.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException(e);
		} catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
		}
		try {
		    if (resp != null)
    	        for (DatabaseInstance d : resp) {
	        		String dummy = null;
	        		dummy = d.getProject(); // qa-project-2

	        		dummy = d.getMaxDiskSize().toString(); // 268435456000
	        		//d.getServerCaCert();

	        		Settings s = d.getSettings(); 
	        		//{"activationPolicy":"ON_DEMAND","backupConfiguration":[{"binaryLogEnabled":false,"enabled":false,"id":"f3b56cf1-e916-4611-971c-61b44c045698","kind":"sql#backupConfiguration","startTime":"12:00"}],"ipConfiguration":{"enabled":false},"kind":"sql#settings","pricingPlan":"PER_USE","replicationType":"SYNCHRONOUS","settingsVersion":"1","tier":"D0"}

	        		dummy = s.getActivationPolicy();  // "ON_DEMAND"
	        		//s.getAuthorizedGaeApplications();
	        		java.util.List<BackupConfiguration> backupConfig = s.getBackupConfiguration();
	        		for (BackupConfiguration backupConfigItem : backupConfig) {
	        			System.out.println(backupConfigItem.getId()); //f3b56cf1-e916-4611-971c-61b44c045698
	        			System.out.println(backupConfigItem.getKind()); //sql#backupConfiguration
	        			System.out.println(backupConfigItem.getStartTime()); // 12:00
	        			System.out.println(backupConfigItem.getBinaryLogEnabled());  // false
	        			System.out.println(backupConfigItem.getEnabled());  // false

	        		}
	        		java.util.List<DatabaseFlags> dbfl = s.getDatabaseFlags();
	        		if (dbfl != null)
		        		for (DatabaseFlags dbflags : dbfl) {
		        			System.out.println(dbflags.getName() + " = " + dbflags.getValue());
		        		}

	        		//s.getIpConfiguration();

	        		LocationPreference lp = s.getLocationPreference();
	        		if (lp != null)
	        			lp.getZone();
	        		dummy = s.getPricingPlan();  // PER_USE or PACKAGE
	        		dummy = s.getReplicationType(); // SYNCHRONOUS
	        		dummy = s.getSettingsVersion().toString(); // 0
	        		dummy = s.getTier(); // D0


	        		Database database = new Database();

	        		database.setAdminUser("root");
	        		Long currentBytesUsed = d.getCurrentDiskSize();
	        		if (currentBytesUsed != null) {
		        		int currentGBUsed = (int) (currentBytesUsed / 1073741824);
		        		database.setAllocatedStorageInGb(currentGBUsed);
	        		}
	        		//database.setConfiguration(configuration);
	        		//database.setCreationTimestamp(creationTimestamp);

	        		String googleDBState = d.getState(); // PENDING_CREATE
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

	        		if (d.getDatabaseVersion().equals("MYSQL_5_5"))
	        			database.setEngine(DatabaseEngine.MYSQL); //  MYSQL55
	        		else if (d.getDatabaseVersion().equals("MYSQL_5_6"))
	        			database.setEngine(DatabaseEngine.MYSQL); // MYSQL56

	        		//database.setHostName(d.getIpAddresses().get(0).getIpAddress()); // BARFS
	        		database.setHostPort(3306);  // Default mysql port
	        		database.setName(d.getInstance()); // dsnrdbms317
	        		database.setProductSize(s.getTier()); // D0
	        		database.setProviderDatabaseId(d.getInstance()); // dsnrdbms317
	        		database.setProviderOwnerId(provider.getContext().getAccountNumber()); // qa-project-2
	        		database.setProviderRegionId(d.getRegion()); // us-central
                    //database.setProviderDataCenterId(providerDataCenterId);

                    //database.setHighAvailability(highAvailability);
                    //database.setMaintenanceWindow(maintenanceWindow);
	        		//database.setRecoveryPointTimestamp(recoveryPointTimestamp);
	        		//database.setSnapshotRetentionInDays(snapshotRetentionInDays);
	        		//database.setSnapshotWindow(snapshotWindow);

					list.add(database);

    	        }
	        return list;
		} catch (Exception e) {
			System.out.println("EXCEPTION " + e);
			e.printStackTrace();
		}
		return null;
	}

    @Override
    public Collection<ConfigurationParameter> listParameters(String forProviderConfigurationId) throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * NOTE: You cannot reuse a name for up to two months after you have deleted an instance.
     */
    @Override
    public void removeDatabase(String providerDatabaseId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        try {
            InstancesDeleteResponse response = sqlAdmin.instances().delete(ctx.getAccountNumber(), providerDatabaseId).execute();
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
        // TODO Auto-generated method stub
    }

    @Override
    public void resetConfiguration(String providerConfigurationId, String... parameters) throws CloudException, InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public void removeConfiguration(String providerConfigurationId) throws CloudException, InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public void restart(String providerDatabaseId, boolean blockUntilDone) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        try {
            GoogleMethod method = new GoogleMethod(provider);
            InstancesRestartResponse response = sqlAdmin.instances().restart(ctx.getAccountNumber(), providerDatabaseId).execute();
            boolean result = method.getRDSOperationComplete(ctx, response.getOperation(), providerDatabaseId);
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
    public RelationalDatabaseCapabilities getCapabilities() throws InternalException, CloudException {
        return new GCERelationalDatabaseCapabilities(provider);
    }

    @Deprecated
    public Iterable<DatabaseProduct> getDatabaseProducts( DatabaseEngine forEngine ) throws CloudException, InternalException {
        return listDatabaseProducts(forEngine);
    }

    @Override
    public DatabaseBackup getUsableBackup(String providerDbId, String beforeTimestamp) throws CloudException, InternalException {
        // TODO candidate for cache optimizating.
        Iterable<DatabaseBackup> backupList = listBackups(null);
        for (DatabaseBackup backup : backupList) 
            if (providerDbId.equals(backup.getProviderBackupId()))
                return backup;

        return null;
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
    public void createFromBackup(DatabaseBackup backup, String databaseCloneToName) throws CloudException, InternalException {

        // TODO Auto-generated method stub

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
            backupRuns = sqlAdmin.backupRuns().list(ctx.getAccountNumber(), forDatabaseId, "").execute();
        }
        catch( Exception e ) {
            throw new CloudException(e);
        }
        try {
            for (BackupRun backupItem : backupRuns.getItems()) {
                DatabaseBackup backup = new DatabaseBackup();
                String instance = backupItem.getInstance();

                backup.setProviderDatabaseId(instance);
                backup.setAdminUser(db.getAdminUser());
                backup.setProviderOwnerId(db.getProviderOwnerId());
                backup.setProviderRegionId(db.getProviderRegionId());

                backup.setConfiguration(backupItem.getBackupConfiguration());
                backup.setDueTime(backupItem.getDueTime().toString());
                backup.setEnqueuedTime(backupItem.getEnqueuedTime().toString());
                backup.setStartTime(backupItem.getStartTime().toString());
                backup.setEndTime(backupItem.getEndTime().toString());

                String status = backupItem.getStatus();
                if (status.equals("SUCCESSFUL")) {
                    backup.setCurrentState(DatabaseBackupState.AVAILABLE);
                } else {
                    backup.setCurrentState(DatabaseBackupState.valueOf(status)); 
                    // this will likely barf first time it gets caught mid backup, 
                    // but with backup windows being 4 hours... will have to wait to catch this one...
                }
                backup.setProviderBackupId(instance + "_" + backup.getDueTime()); // artificial concat of db name and timestamp
                OperationError error = backupItem.getError(); // null
                if (error != null) 
                    backup.setCurrentState(DatabaseBackupState.ERROR);


                // db.isHighAvailability();
                // Unknown what to do with
                //String config = backup.getBackupConfiguration(); // 991a6ae6-17c7-48a1-8410-9807b8e3e2ad
                //Map<String, Object> keys = backup.getUnknownKeys();
                //int retentionDays = db.getSnapshotRetentionInDays();
                //String kind = backup.getKind(); // sql#backupRun
                //snapShot.setStorageInGigabytes(storageInGigabytes);  // N.A.

                backups.add(backup);
            }
        }
        catch( Exception e ) {
            throw new InternalException(e); // TODO NPE if no backups present in any of the databases existing!!!!
        }

        return backups;  
    }

    /* 
     * WIP
     */
    @Override
    public void restoreBackup(DatabaseBackup backup) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        //2012-11-15T16:19:00.094Z
        String when = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000Z").format(new Date());
        when = "2014-10-09T19:55:05.000Z";
        //GoogleMethod method = new GoogleMethod(provider);
        InstancesRestoreBackupResponse response = null;
        try {
            String acct = ctx.getAccountNumber();
            // from around line 920
            // {"backupConfiguration":"4be91d6f-3ab7-4a21-b082-fad698a16cb0","dueTime":"2014-10-02T10:00:00.209Z","endTime":"2014-10-02T11:58:25.670Z","enqueuedTime":"2014-10-02T11:58:01.227Z","instance":"stateless-test-database","kind":"sql#backupRun","startTime":"2014-10-02T11:58:01.230Z","status":"SUCCESSFUL"}
            // {"backupConfiguration":"4be91d6f-3ab7-4a21-b082-fad698a16cb0","dueTime":"2014-10-08T10:00:00.134Z","enqueuedTime":"2014-10-08T12:08:57.283Z","instance":"stateless-test-database","kind":"sql#backupRun","status":"SKIPPED"}
            // only works when "status":"SUCCESSFUL" is used to feed it...
            response = sqlAdmin.instances().restoreBackup(acct, backup.getProviderDatabaseId(), "4be91d6f-3ab7-4a21-b082-fad698a16cb0", "2014-10-02T10:00:00.209Z").execute();
            //boolean result = method.getRDSOperationComplete(ctx, response.getOperation(), providerDatabaseId); // Exception e -> The client is not authorized to make this request.

        } catch ( IOException e ) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception e) {
            System.out.println(e);
        }
        

        /* project Project ID of the project that contains the instance.
         * instance Cloud SQL instance ID. This does not include the project ID.
         * backupConfiguration The identifier of the backup configuration. This gets generated automatically when a backup configuration is created.
         * dueTime The time when this run is due to start in RFC 3339 format, for example 2012-11-15T16:19:00.094Z.
         */
        
    }
}

