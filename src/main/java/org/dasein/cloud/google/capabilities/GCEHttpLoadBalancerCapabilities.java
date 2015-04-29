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

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.ci.HttpLoadBalancerCapabilities;
import org.dasein.cloud.google.Google;

public class GCEHttpLoadBalancerCapabilities extends AbstractCapabilities<Google> implements HttpLoadBalancerCapabilities {

    public GCEHttpLoadBalancerCapabilities(Google provider) {
        super(provider);
    }

    @Override
    public boolean supportsHttpTraffic() {
        return true;
    }

    @Override
    public boolean supportsHttpsTraffic() {
        return false;
    }

    @Override
    public boolean supportsHealthChecks() {
        return true;
    }

    @Override
    public boolean supportsMoreThanOneHealthCheck() {
        return false;
    }

    @Override
    public boolean supportsBackendServices() {
        return true;
    }

    @Override
    public boolean supportsMoreThanOneBackendService() {
        return true;
    }

    @Override
    public boolean supportsUrlSets() {
        return true;
    }

    @Override
    public boolean supportsMoreThanOneUrlSet() {
        return true;
    }

    @Override
    public boolean supportsTargetHttpProxies() {
        return true;
    }

    @Override
    public boolean supportsMoreThanOneTargetHttpProxy() {
        return true;
    }

    @Override
    public boolean supportsForwardingRules() {
        return true;
    }

    @Override
    public boolean supportsMoreThanOneForwardingRule() {
        return true;
    }

    @Override
    public String getProviderTermForHttpLoadBalancer(Locale locale) {
        return "HTTP Load Balancer";
    }

    @Override
    public Iterable<String> listSupportedHttpPorts() {
        return Collections.unmodifiableList(Arrays.asList("80", "8080"));
    }

    @Override
    public boolean supportsUsingExistingHealthCheck() {
        return true;
    }

    @Override
    public boolean supportsUsingExistingBackendService() {
        return true;
    }


}
