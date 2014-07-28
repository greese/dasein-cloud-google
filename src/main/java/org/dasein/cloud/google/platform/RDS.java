package org.dasein.cloud.google.platform;

import java.util.*;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.TimeWindow;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.ConfigurationParameter;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseConfiguration;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.DatabaseSnapshot;
import org.dasein.cloud.platform.DatabaseState;
import org.dasein.cloud.platform.RelationalDatabaseSupport;
import org.dasein.cloud.util.APITrace;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.SQLAdmin.Builder;
import com.google.api.services.sqladmin.SQLAdmin.Instances;
import com.google.api.services.sqladmin.SQLAdmin.Instances.List;
import com.google.api.services.sqladmin.model.BackupConfiguration;
import com.google.api.services.sqladmin.model.DatabaseFlags;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.InstancesDeleteResponse;
import com.google.api.services.sqladmin.model.InstancesInsertResponse;
import com.google.api.services.sqladmin.model.InstancesListResponse;
import com.google.api.services.sqladmin.model.IpConfiguration;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.LocationPreference;
import com.google.api.services.sqladmin.model.Settings;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.api.services.sqladmin.model.Tier;
import com.google.api.services.sqladmin.model.TiersListResponse;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;

import java.io.IOException;
import java.io.PrintWriter;



import java.io.IOException;
import java.io.PrintWriter;


public class RDS implements RelationalDatabaseSupport {
    static private volatile ArrayList<DatabaseEngine> engines = null;
    private volatile ArrayList<DatabaseProduct> databaseProducts = null;
    private Google provider;

	public RDS(Google provider) {
        this.provider = provider;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addAccess(String providerDatabaseId, String sourceCidr)
			throws CloudException, InternalException {
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
		
			content.setInstance(dataSourceName);
			content.setDatabaseVersion(databaseVersion);				// String MYSQL_5_5 or MYSQL_5_6
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

				java.util.List<DatabaseFlags> databaseFlags;

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
	public String createFromLatest(String dataSourceName,
			String providerDatabaseId, String productSize,
			String providerDataCenterId, int hostPort)
			throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String createFromSnapshot(String dataSourceName,
			String providerDatabaseId, String providerDbSnapshotId,
			String productSize, String providerDataCenterId, int hostPort)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String createFromTimestamp(String dataSourceName,
			String providerDatabaseId, long beforeTimestamp,
			String productSize, String providerDataCenterId, int hostPort)
			throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DatabaseConfiguration getConfiguration(String providerConfigurationId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

    public Database getDatabase(String providerDatabaseId) throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.getDatabase");
        try {
            if( providerDatabaseId == null ) {
                return null;
            }
            Iterable<Database> dbs = listDatabases(providerDatabaseId);
            if (dbs != null)
	            for( Database database : dbs) 
	            	if (database != null)
		                if( database.getProviderDatabaseId().equals(providerDatabaseId) ) 
		                    return database;

            return null;
        } catch (Exception e) {
        	System.out.println("EXCEPTION " + e);
        	e.printStackTrace();
        	return null;
        }

        finally {
            APITrace.end();
        }
    }

	@Override
	public Iterable<DatabaseEngine> getDatabaseEngines() throws CloudException, InternalException {
        ArrayList<DatabaseEngine> tmp = engines;

        if( tmp == null ) {
            tmp = new ArrayList<DatabaseEngine>(); 
            tmp.add(DatabaseEngine.MYSQL);
            engines = tmp;
        }
        return engines;
	}

	@Override
    public String getDefaultVersion(DatabaseEngine forEngine) throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.getDefaultVersion");
        try {
            
            return "MYSQL_5_5"; // can also be "MYSQL_5_6"
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public Iterable<String> getSupportedVersions(DatabaseEngine forEngine)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	
	
	
	
	
	@Override
	public Iterable<DatabaseProduct> getDatabaseProducts(DatabaseEngine forEngine) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		ArrayList<DatabaseProduct> products = databaseProducts;
		if (products == null) {
			products = new ArrayList<DatabaseProduct>();

			DatabaseProduct product;
			product = new DatabaseProduct("D0", "128MB RAM"); // D0 D1 D2 D4 D8 D16 D32*
		    product.setEngine(forEngine);
		    product.setEngine(DatabaseEngine.MYSQL56);
		    product.setHighAvailability(false);
		    product.setStandardHourlyRate(0.025f);
		    product.setStandardIoRate(0.10f);   // $0.10 per Million per month
		    product.setStandardStorageRate(0.24f);  // 0.24 per GB per month
		    product.setStorageInGigabytes(250);
		    products.add(product);
		}
		return products; 

	}
	
	
	
	
	
	
	
	/*
	java.util.List<Tier> resp;
	try {
		
		
		DatabaseInstance i = new DatabaseInstance();
		// resp = sqlAdmin.tiers().list(ctx.getAccountNumber()).execute().getItems(); <--- lists details about instantiated databases
		resp = sqlAdmin.tiers().list(ctx.getAccountNumber()).execute().getItems();  // null exception here...
	} catch (IOException e) {
			if (e.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException(e);
	} catch (Exception ex) {
        throw new CloudException("Access denied.  Verify GCE Credentials exist.");
	}

    for (Tier t : resp) {


		//DatabaseProduct product;
		System.out.println(
			t.getDiskQuota() + " " +
			t.getKind() + " " +
			t.getRAM() + " " +
			t.getTier());
        //product = new DatabaseProduct("db.m1.small", "64-bit, 1.7GB RAM, 1x1.0 GHz CPU Core");
        //product.setEngine(engine);
        //product.setHighAvailability(false);
   
        //product.setStandardHourlyRate(us ? 0.11f : 0.12f);
        //product.setStandardIoRate(us ? 0.10f : 0.11f);
        //product.setStandardStorageRate(us ? 0.10f : 0.11f);
        //product.setStorageInGigabytes(0);
        //databaseProducts.add(product);
	}
	*/
	
	
	
	@Override
	public String getProviderTermForDatabase(Locale locale) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getProviderTermForSnapshot(Locale locale) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DatabaseSnapshot getSnapshot(String providerDbSnapshotId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSupportsFirewallRules() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSupportsHighAvailability() throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSupportsLowAvailability() throws CloudException,
			InternalException {
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Iterable<String> listAccess(String toProviderDatabaseId) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<DatabaseConfiguration> listConfigurations()
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<ResourceStatus> listDatabaseStatus() throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
    public Iterable<Database> listDatabases() throws CloudException, InternalException {
        return listDatabases(null);
    }
    
    private Iterable<Database> listDatabases(String targetId) throws CloudException, InternalException {
        System.out.println("in list databases fargitid = " + targetId);
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
    	        	if ((targetId == null) || (targetId.equals(d.getInstance()))) {
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
    	        			database.setEngine(DatabaseEngine.MYSQL55);
    	        		else if (d.getDatabaseVersion().equals("MYSQL_5_6"))
    	        			database.setEngine(DatabaseEngine.MYSQL56);

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
    	        }
	        return list;
		} catch (Exception e) {
			System.out.println("EXCEPTION " + e);
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Collection<ConfigurationParameter> listParameters(
			String forProviderConfigurationId) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<DatabaseSnapshot> listSnapshots(
			String forOptionalProviderDatabaseId) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void removeConfiguration(String providerConfigurationId)
			throws CloudException, InternalException {
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
	public void removeSnapshot(String providerSnapshotId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resetConfiguration(String providerConfigurationId,
			String... parameters) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restart(String providerDatabaseId, boolean blockUntilDone)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void revokeAccess(String providerDatabaseId, String sourceCide)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateConfiguration(String providerConfigurationId,
			ConfigurationParameter... parameters) throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DatabaseSnapshot snapshot(String providerDatabaseId, String name)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

}
