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

import org.dasein.cloud.ci.AbstractReplicapoolSupportCapabilities;

/**
 * @author Roger Unwin
 * @version 2015.03 initial version
 * @since 2015.03
 */
public class GCEReplicapoolCapabilities implements AbstractReplicapoolSupportCapabilities {

    @Override
    public String getAccountNumber() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getRegionId() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean supportsHttpTraffic() {
        return true;
    }

    public boolean supportsHttpsTraffic() {
        return true;
    }

    public boolean supportsMetadata() {
        return true;
    }

    public boolean supportsSshKeys() {
        return true;
    }

    public boolean supportsTags() {
        return true;
    }

    public boolean supportsSsdDisk() {
        return true;
    }

    public boolean supportsStandardDisk() {
        return true;
    }

    public boolean supportsDeleteDiskOnTerminate() {
        return true;
    }

    public boolean supportsReadOnlySharedDisks() {
        return true;
    }

    public boolean supportsVmAutomaticRestart() {
        return true;
    }

    public boolean supportsMigrateVmOnMaintence() {
        return true;
    }

    public boolean supportsTemplates() {
        return true;
    }

    public boolean supportsRegions() { // Zone
        return true;
    }

    public boolean supportsCreateFromInstance() {
        return true;
    }

    public boolean supportsAutoScaling() {
        return true;
    }

    // requires supports is list 
}
