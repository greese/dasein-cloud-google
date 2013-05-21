/**
 * ========= CONFIDENTIAL =========
 *
 * Copyright (C) 2012-2013 enStratus Networks Inc - ALL RIGHTS RESERVED
 *
 * ====================================================================
 *  NOTICE: All information contained herein is, and remains the
 *  property of enStratus Networks Inc. The intellectual and technical
 *  concepts contained herein are proprietary to enStratus Networks Inc
 *  and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination
 *  of this information or reproduction of this material is strictly
 *  forbidden unless prior written permission is obtained from
 *  enStratus Networks Inc.
 * ====================================================================
 */

package org.dasein.cloud.google.compute;

import javax.annotation.Nonnull;

import org.dasein.cloud.compute.AbstractComputeServices;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.GoogleDiskSupport;
import org.dasein.cloud.google.compute.server.GoogleImageSupport;
import org.dasein.cloud.google.compute.server.GoogleServerSupport;
import org.dasein.cloud.google.compute.server.GoogleSnapshotSupport;

/**
 * Implements the compute services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleCompute extends AbstractComputeServices {
	private Google provider;

    public GoogleCompute(Google provider) { this.provider = provider; }

    public @Nonnull GoogleServerSupport getVirtualMachineSupport() {
        return new GoogleServerSupport(provider);
    }
    
    public @Nonnull GoogleDiskSupport getVolumeSupport() {
        return new GoogleDiskSupport(provider);
    }
    
    public @Nonnull GoogleSnapshotSupport getSnapshotSupport() {
        return new GoogleSnapshotSupport(provider);
    }
    
    public @Nonnull GoogleImageSupport getImageSupport() {
        return new GoogleImageSupport(provider);
    }


}
