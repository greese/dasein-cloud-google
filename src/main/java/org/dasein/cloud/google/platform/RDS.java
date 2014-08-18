package org.dasein.cloud.google.platform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.TimeWindow;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.ConfigurationParameter;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseConfiguration;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.DatabaseSnapshot;
import org.dasein.cloud.platform.DatabaseSnapshotState;
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
	public void addAccess(String providerDatabaseId, String sourceCidr) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
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

			sqlAdmin.instances().insert(ctx.getAccountNumber(), content).execute();

			return dataSourceName;
		} catch (IOException e) {
			if (e.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
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
	public String createFromSnapshot(String dataSourceName, String providerDatabaseId, String providerDbSnapshotId, String productSize, String providerDataCenterId, int hostPort) throws CloudException, InternalException {
		// TODO Auto-generated method stub
	    
	    // this needs a method...
	    // sqlAdmin.instances().restoreBackup(arg0, arg1, arg2, arg3)
	    
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
	    APITrace.begin(provider, "RDBMS.getDatabaseProducts");
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


        //product = new DatabaseProduct("db.m1.small", "64-bit, 1.7GB RAM, 1x1.0 GHz CPU Core");
        //product.setEngine(engine);
        //product.setHighAvailability(false);
        //product.setStandardHourlyRate(us ? 0.11f : 0.12f);
        //product.setStandardIoRate(us ? 0.10f : 0.11f);
        //product.setStandardStorageRate(us ? 0.10f : 0.11f);
        //product.setStorageInGigabytes(0);
        //databaseProducts.add(product);

	
	
	
	@Override
	public String getProviderTermForDatabase(Locale locale) {
	    // TODO language localization
		return "Cloud SQL";
	}

	@Override
	public String getProviderTermForSnapshot(Locale locale) {
		return "Backup";
	}

	@Override
	public DatabaseSnapshot getSnapshot(String providerDbSnapshotId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

	    try {
            BackupRun backup = sqlAdmin.backupRuns().get("", "", "", "").execute();
            System.out.println("inspect backup");
        }
        catch( IOException e ) {
            // TODO Auto-generated catch block
            System.out.println("inspect exception to work out what above call wants as params");
            e.printStackTrace();
        }
		return null;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		// TODO Verify that i interpreted this correctly.
		return true;
	}

	@Override
	public boolean isSupportsFirewallRules() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSupportsHighAvailability() throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSupportsLowAvailability() throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSupportsMaintenanceWindows() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
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
		return true;
	}

	@Override
	public Iterable<String> listAccess(String toProviderDatabaseId) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<DatabaseConfiguration> listConfigurations() throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<ResourceStatus> listDatabaseStatus() throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
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

	public ArrayList<DatabaseSnapshot> getSnapshotForDatabase(String forDatabaseId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        ArrayList<DatabaseSnapshot> snapshots = new ArrayList<DatabaseSnapshot>();

        Database db = getDatabase(forDatabaseId);
        BackupRunsListResponse results = null;
        try {
            results = sqlAdmin.backupRuns().list(ctx.getAccountNumber(), forDatabaseId, "").execute();
        }
        catch( Exception e ) {
            throw new CloudException(e);
        }
        try {
            for (BackupRun backup : results.getItems()) {
                DatabaseSnapshot snapShot = new DatabaseSnapshot();
                String instance = backup.getInstance();
                snapShot.setProviderDatabaseId(instance);

                snapShot.setAdminUser(db.getAdminUser());
                snapShot.setProviderOwnerId(db.getProviderOwnerId());
                snapShot.setProviderRegionId(db.getProviderRegionId());
                snapShot.setProviderSnapshotId(snapShot.getProviderSnapshotId());
                String status = backup.getStatus();
                if (status.equals("SUCCESSFUL")) {
                    snapShot.setCurrentState(DatabaseSnapshotState.AVAILABLE);
                    snapShot.setSnapshotTimestamp(backup.getStartTime().getValue());
                } else {
                    snapShot.setCurrentState(DatabaseSnapshotState.valueOf(status)); 
                    // this will likely barf first time it gets caught mid backup, 
                    // but with backup windows being 4 hours... will have to wait to catch this one...
                    snapShot.setSnapshotTimestamp(backup.getDueTime().getValue());
                }
                OperationError error = backup.getError(); // null
                if (error != null) 
                    snapShot.setCurrentState(DatabaseSnapshotState.ERROR);



                // Unknown what to do with
                //String config = backup.getBackupConfiguration(); // 991a6ae6-17c7-48a1-8410-9807b8e3e2ad
                //Map<String, Object> keys = backup.getUnknownKeys();
                //int retentionDays = db.getSnapshotRetentionInDays();
                //String kind = backup.getKind(); // sql#backupRun
                //snapShot.setStorageInGigabytes(storageInGigabytes);  // N.A.

                snapshots.add(snapShot);
            }
        }
        catch( Exception e ) {
            throw new InternalException(e);
        }

        return snapshots;
	}

	@Override
	public Iterable<DatabaseSnapshot> listSnapshots(String forOptionalProviderDatabaseId) throws CloudException, InternalException {
        ArrayList<DatabaseSnapshot> snapshots = new ArrayList<DatabaseSnapshot>();
        if (forOptionalProviderDatabaseId == null) {
            Iterable<Database> dataBases = listDatabases();
            for (Database db : dataBases) 
                snapshots.addAll(getSnapshotForDatabase(db.getProviderDatabaseId()));
        } else 
            snapshots = getSnapshotForDatabase(forOptionalProviderDatabaseId);

		return snapshots;
	}

	@Override
	public void removeConfiguration(String providerConfigurationId) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeDatabase(String providerDatabaseId) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();
		SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

		try {
			sqlAdmin.instances().delete(ctx.getAccountNumber(), providerDatabaseId).execute();
		} catch (IOException e) {
			if (e.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException(e);
		} catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
		}
	}

	
	
	
	
	
	
	@Override
	public void removeSnapshot(String providerSnapshotId) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resetConfiguration(String providerConfigurationId, String... parameters) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restart(String providerDatabaseId, boolean blockUntilDone) throws CloudException, InternalException {
		// TODO Auto-generated method stub
	    //sqlAdmin.instances().restart(arg0, arg1)
		
	}

	@Override
	public void revokeAccess(String providerDatabaseId, String sourceCide) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateConfiguration(String providerConfigurationId, ConfigurationParameter... parameters) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DatabaseSnapshot snapshot(String providerDatabaseId, String name) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

    @Override
    public RelationalDatabaseCapabilities getCapabilities() throws InternalException, CloudException {
        // TODO Auto-generated method stub
        return null;
    }

    @Deprecated
    public Iterable<DatabaseProduct> getDatabaseProducts( DatabaseEngine forEngine ) throws CloudException, InternalException {
        throw new CloudException("Why are you using getDatabaseProducts instead of listDatabaseProducts");
    }
  
}
