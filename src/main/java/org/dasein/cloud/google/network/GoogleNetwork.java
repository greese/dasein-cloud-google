/**
 * Copyright (C) 2012-2013 Dell, Inc
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

    @Override
    public @Nonnull GoogleIPSupport getIpAddressSupport(){
        return new GoogleIPSupport(cloud);
    }

}
