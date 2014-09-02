package org.dasein.cloud.google.capabilities;

import java.util.Locale;

import javax.annotation.Nonnull;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;

public class GCERelationalDatabaseCapabilities extends AbstractCapabilities<Google> implements RelationalDatabaseCapabilities {

    public GCERelationalDatabaseCapabilities( @Nonnull Google provider ) {
        super(provider);
    }

    @Override
    public String getProviderTermForDatabase( Locale locale ) {
        return "database";
    }

    @Override
    public String getProviderTermForSnapshot( Locale locale ) {
        //https://developers.google.com/cloud-sql/docs/backup-recovery#cloudsqladmin
        return null;  // possibly "point-in-time recovery"
    }

    @Override
    public boolean isSupportsFirewallRules() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSupportsHighAvailability() throws CloudException, InternalException {
        // TODO Understand this better...
        /*
         * Database db = getDatabase(forDatabaseId);
         * db.isHighAvailability()
         */
        return true;
    }

    @Override
    public boolean isSupportsLowAvailability() throws CloudException, InternalException {
        // TODO Understand this better...
        /*
         * Database db = getDatabase(forDatabaseId);
         * db.isHighAvailability()
         */
        return true;
    }

    @Override
    public boolean isSupportsMaintenanceWindows() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSupportsAlterDatabase() throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isSupportsSnapshots() throws CloudException, InternalException {
        return false; // with binary logging supports "point-in-time recovery"
    }

    @Override
    public String getProviderTermForBackup( Locale locale ) {
        return "Backup";
    }

    @Override
    public boolean isSuppotsDatabaseBackups() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSuppotsScheduledDatabaseBackups() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSuppotsDemandBackups() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSupportsRestoreBackup() throws CloudException, InternalException {
        return true;
    }
}
