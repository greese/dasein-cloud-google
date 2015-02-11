package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.ci.AbstractReplicapoolSupportCapabilities;

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
