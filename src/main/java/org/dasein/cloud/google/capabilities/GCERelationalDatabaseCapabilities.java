/**
 * Copyright (C) 2012-2015 Dell, Inc
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

package org.dasein.cloud.google.capabilities;

import java.util.Locale;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.platform.AbstractRelationalDatabaseCapabilities;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;

public class GCERelationalDatabaseCapabilities extends AbstractRelationalDatabaseCapabilities<Google> implements RelationalDatabaseCapabilities {

    private Google provider;

    public GCERelationalDatabaseCapabilities( @Nonnull Google provider ) {
        super(provider);
        this.provider = provider;
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
    public String getProviderTermForBackup( Locale locale ) {
        return "Backup";
    }

    @Override
    public boolean supportsFirewallRules() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsHighAvailability() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsLowAvailability() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsMaintenanceWindows() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsAlterDatabase() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsSnapshots() throws CloudException, InternalException {
        return false; // with binary logging supports "point-in-time recovery"
    }

    @Override
    public boolean supportsDatabaseBackups() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsScheduledDatabaseBackups() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsDemandBackups() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsRestoreBackup() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsDeleteBackup() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsBackupConfigurations() throws CloudException, InternalException {
        return false;
    }

    @Override
    public String getAccountNumber() {
        ProviderContext ctx = provider.getContext(); 
        return ctx.getAccountNumber();
    }

    @Override
    public String getRegionId() {
        ProviderContext ctx = provider.getContext(); 
        return ctx.getRegionId();
    }
}
