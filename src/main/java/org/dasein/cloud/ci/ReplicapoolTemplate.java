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
import org.dasein.cloud.Tag;

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

    public ReplicapoolTemplate() { 
        // name, 
        //metadata hash[]
        // boolean allowHttps
        // boolean allowHttp
        // machine type
        // boot disk Image
        // Boot Disk Type
        // deletedisk on instance termination
        // External Ip (none, ephemeral)
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
}
