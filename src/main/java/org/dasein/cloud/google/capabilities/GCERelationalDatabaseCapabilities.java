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
        return "point-in-time recovery";
    }

    @Override
    public boolean isSupportsFirewallRules() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSupportsHighAvailability() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSupportsLowAvailability() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSupportsMaintenanceWindows() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSupportsAlterDatabase() throws CloudException, InternalException {
        return true;
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
    public boolean isSupportsDatabaseBackups() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSupportsScheduledDatabaseBackups() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSupportsDemandBackups() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSupportsRestoreBackup() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isSupportsDeleteBackup() throws CloudException, InternalException {
        return false;
    }
}
