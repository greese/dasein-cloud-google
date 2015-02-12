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

package org.dasein.cloud.ci;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.VirtualMachineProduct;

import com.google.api.services.compute.model.Metadata;

/**
 * Implements the Replicapool Templates supported in the Google API.
 * @author Roger Unwin
 * @version 2015.03 initial version
 * @since 2015.03
 */

public class ReplicapoolTemplate {

    private boolean automaticRestart = false;
    private boolean maintenenceMigration = false;
    private boolean ipForwarding = false;
    private String[] readonlyDisks = new String[0];
    private Tag[] tags = new Tag[0];
    private String[] sshKeys = new String[0];
    private String poolName = null;
    private Metadata metadata;
    private boolean allowHttp = false;
    private boolean allowHttps = false;
    private VirtualMachineProduct vmProduct;
    private String providerImageId;
    private String bootDiskType;
    private boolean deleteDiskOnTerminate;
    private boolean assignEphemeralExternalIp;

    public ReplicapoolTemplate(@Nonnull String poolName,
                               @Nonnull Metadata metadata,
                               @Nonnull boolean allowHttp,
                               @Nonnull boolean allowHttps,
                               @Nonnull VirtualMachineProduct vmProduct,
                               @Nonnull String providerImageId,
                               @Nonnull String bootDiskType,         // Standard persistent disk or SSD
                               @Nonnull boolean deleteDiskOnTerminate,
                               @Nonnull boolean assignEphemeralExternalIp) {
        this.poolName = poolName;
        this.metadata = metadata;
        this.allowHttp = allowHttp;
        this.allowHttps = allowHttps;
        this.vmProduct = vmProduct;
        this.providerImageId = providerImageId;
        this.bootDiskType = bootDiskType;
        this.deleteDiskOnTerminate = deleteDiskOnTerminate;
        this.assignEphemeralExternalIp = assignEphemeralExternalIp;
    }

    public ReplicapoolTemplate withSshKeys(@Nonnull String[] sshKeys) {
        this.sshKeys = sshKeys;
        return this;
    }

    public String[] getSshKeys() {
        return sshKeys;
    }

    public ReplicapoolTemplate withTags(Tag... tags) {
        this.tags = tags;
        return this;
    }

    public Tag[] getTags() {
        return tags;
    }

    public ReplicapoolTemplate withReadonlyDisks(String[] readonlyDisks) {
        this.readonlyDisks = readonlyDisks;
        return this;
    }

    public String[] getReadonlyDisks() {
        return readonlyDisks;
    }

    public ReplicapoolTemplate withIpForwarding(boolean ipForwarding) {
        this.ipForwarding = ipForwarding;
        return this;
    }

    public boolean hasIpForwarding() {
        return ipForwarding;
    }

    public ReplicapoolTemplate withAutomaticRestart(boolean automaticRestart) {
        this.automaticRestart = automaticRestart;
        return this;
    }

    public boolean hasAutomaticRestart() {
        return automaticRestart;
    }

    public ReplicapoolTemplate withMaintenenceMigration(boolean maintenenceMigration) {
        this.maintenenceMigration = maintenenceMigration;
        return this;
    }

    public boolean hasMaintenenceMigration() {
        return maintenenceMigration;
    }

    public String getPoolName() {
        return poolName;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public boolean getAllowHttp() {
        return allowHttp;
    }

    public boolean getAllowHttps() {
        return allowHttps;
    }

    public VirtualMachineProduct getVmProduct() {
        return vmProduct;
    }

    public String getProviderImageId() {
        return providerImageId;
    }

    public String getBootDiskType() {
        return bootDiskType;
    }

    public boolean getDeleteDiskOnTerminate() {
        return deleteDiskOnTerminate;
    }

    public boolean getAssignEphemeralExternalIp() {
        return assignEphemeralExternalIp;
    }

    public boolean create(CloudProvider provider) {
        return false;
        // TODO Auto-generated method stub
    }

    public boolean remove(CloudProvider provider) {
        return false;
        // TODO Auto-generated method stub
    }
}
