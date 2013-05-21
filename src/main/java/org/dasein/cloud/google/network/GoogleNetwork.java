package org.dasein.cloud.google.network;

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

import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.AbstractNetworkServices;
import org.dasein.cloud.network.DNSSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class GoogleNetwork extends AbstractNetworkServices {
    private Google cloud;
    
    public GoogleNetwork(Google cloud) { this.cloud = cloud; }

    
    @Override
    public @Nonnull GoogleNetworkSupport getVlanSupport() {
        return new GoogleNetworkSupport(cloud);
    }
    
    @Override
    public @Nonnull GoogleFirewallSupport getFirewallSupport() {
        return new GoogleFirewallSupport(cloud);
    }

}
