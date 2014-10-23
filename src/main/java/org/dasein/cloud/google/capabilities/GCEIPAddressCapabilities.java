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

package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.IPAddressCapabilities;
import org.dasein.cloud.network.IPVersion;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Locale;

public class GCEIPAddressCapabilities extends AbstractCapabilities<Google> implements IPAddressCapabilities{
    public GCEIPAddressCapabilities(@Nonnull Google cloud){super(cloud);}

    @Nonnull @Override public String getProviderTermForIpAddress(@Nonnull Locale locale){
        return "address";
    }

    @Nonnull @Override public Requirement identifyVlanForVlanIPRequirement() throws CloudException, InternalException{
        return Requirement.NONE;
    }

    @Override public boolean isAssigned(@Nonnull IPVersion version) throws CloudException, InternalException{
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Override public boolean canBeAssigned(@Nonnull VmState vmState) throws CloudException, InternalException{
        if(vmState.equals(VmState.RUNNING))return true;
        return false;
    }

    @Override public boolean isAssignablePostLaunch(@Nonnull IPVersion version) throws CloudException, InternalException{
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Override public boolean isForwarding(IPVersion version) throws CloudException, InternalException{
        // cannot port forward from one external ip to a specific vm without using load balancer magic.
        return false;
    }

    @Override public boolean isRequestable(@Nonnull IPVersion version) throws CloudException, InternalException{
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Nonnull @Override public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException{
        return Collections.singletonList(IPVersion.IPV4);
    }

    @Override public boolean supportsVLANAddresses(@Nonnull IPVersion ofVersion) throws InternalException, CloudException{
        return false;
    }
}
