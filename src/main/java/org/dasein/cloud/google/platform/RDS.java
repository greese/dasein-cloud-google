package org.dasein.cloud.google.platform;

import java.util.Collection;
import java.util.Locale;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.TimeWindow;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.ConfigurationParameter;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseConfiguration;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.DatabaseSnapshot;
import org.dasein.cloud.platform.RelationalDatabaseSupport;

public class RDS implements RelationalDatabaseSupport {

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
	public String createFromScratch(String dataSourceName,
			DatabaseProduct product, String databaseVersion,
			String withAdminUser, String withAdminPassword, int hostPort)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
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

	@Override
	public Database getDatabase(String providerDatabaseId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<DatabaseEngine> getDatabaseEngines() throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultVersion(DatabaseEngine forEngine)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<String> getSupportedVersions(DatabaseEngine forEngine)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<DatabaseProduct> getDatabaseProducts(
			DatabaseEngine forEngine) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

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
	public Iterable<String> listAccess(String toProviderDatabaseId)
			throws CloudException, InternalException {
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
	public Iterable<Database> listDatabases() throws CloudException,
			InternalException {
		// TODO Auto-generated method stub
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
	public void removeDatabase(String providerDatabaseId)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
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
