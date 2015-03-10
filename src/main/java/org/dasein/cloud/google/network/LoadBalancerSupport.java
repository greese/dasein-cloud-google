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

package org.dasein.cloud.google.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.capabilities.GCELoadBalancerCapabilities;
import org.dasein.cloud.network.AbstractLoadBalancerSupport;
import org.dasein.cloud.network.HealthCheckFilterOptions;
import org.dasein.cloud.network.HealthCheckOptions;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddress;
import org.dasein.cloud.network.LbAlgorithm;
import org.dasein.cloud.network.LbEndpointState;
import org.dasein.cloud.network.LbEndpointType;
import org.dasein.cloud.network.LbListener;
import org.dasein.cloud.network.LbPersistence;
import org.dasein.cloud.network.LbProtocol;
import org.dasein.cloud.network.LbType;
import org.dasein.cloud.network.LoadBalancer;
import org.dasein.cloud.network.LoadBalancerAddressType;
import org.dasein.cloud.network.LoadBalancerCapabilities;
import org.dasein.cloud.network.LoadBalancerCreateOptions;
import org.dasein.cloud.network.LoadBalancerEndpoint;
import org.dasein.cloud.network.LoadBalancerHealthCheck;
import org.dasein.cloud.network.LoadBalancerHealthCheck.HCProtocol;
import org.dasein.cloud.network.LoadBalancerState;
import org.dasein.cloud.util.APITrace;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.ForwardingRuleList;
import com.google.api.services.compute.model.HealthCheckReference;
import com.google.api.services.compute.model.HttpHealthCheck;
import com.google.api.services.compute.model.InstanceReference;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.TargetPool;
import com.google.api.services.compute.model.TargetPoolList;
import com.google.api.services.compute.model.TargetPoolsAddHealthCheckRequest;
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest;
import com.google.api.services.compute.model.TargetPoolsRemoveHealthCheckRequest;
import com.google.api.services.compute.model.TargetPoolsRemoveInstanceRequest;

/**
 * @author Roger Unwin
 *
 */

public class LoadBalancerSupport extends AbstractLoadBalancerSupport<Google>  {
    static private final Logger logger = Logger.getLogger(AbstractLoadBalancerSupport.class);

    private volatile transient GCELoadBalancerCapabilities capabilities;
    private Google provider = null;
    private ProviderContext ctx = null;
    private Compute gce = null;

    public LoadBalancerSupport(Google provider) {
        super(provider);
        this.provider = provider;

        ctx = provider.getContext(); 
    }

    @Override
    public boolean isDataCenterLimited() {
        return false;
    }

    @Nonnull
    public LoadBalancerCapabilities getCapabilities() throws CloudException, InternalException {
        if( capabilities == null ) {
            capabilities = new GCELoadBalancerCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public String getProviderTermForLoadBalancer(Locale locale) {
        return "target pool";
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    private void releaseLoadBalancerIp(@Nonnull LoadBalancer lb) throws CloudException, InternalException {
        try {
            if ((lb.getAddress() != null) && (!lb.getAddress().equals(""))) {
                IPAddressSupport ipSupport = provider.getNetworkServices().getIpAddressSupport();
                String ip = lb.getAddress();
                if (ip != null)
                    ipSupport.releaseFromPool(ipSupport.getIpAddressIdFromIP(ip, provider.getContext().getRegionId()));
            }
        } catch (Exception e) {
            throw new CloudException(e);
        }
    }

	private void releaseLoadBalancerIp(@Nonnull LoadBalancer lb) throws CloudException, InternalException {
	    
        try {
            if ((lb.getAddress() != null) && (!lb.getAddress().equals(""))) {
                IPAddressSupport ipSupport = provider.getNetworkServices().getIpAddressSupport();
                String ip = lb.getAddress();
                if (ip != null)
                    ipSupport.releaseFromPool(ipSupport.getIpAddressIdFromIP(ip, provider.getContext().getRegionId()));
            }
        } catch (Exception e) {
            throw new CloudException(e);
        }
	}
	
    @Override
    public void removeLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.removeLoadBalancer");

        gce = provider.getGoogleCompute();

        // Release IP used by Forwarding Rules [probably not desired]
        LoadBalancer lb = this.getLoadBalancer(loadBalancerId);

        List<String> forwardingRuleNames = getForwardingRules(loadBalancerId);
        for (String forwardingRuleName : forwardingRuleNames)
            if (forwardingRuleName != null)
                removeLoadBalancerForwardingRule(forwardingRuleName); 

        // Release IP used by Forwarding Rules [probably not desired]
        releaseLoadBalancerIp(lb);

        try {
            GoogleMethod method = new GoogleMethod(provider);
            Operation job = gce.targetPools().delete(ctx.getAccountNumber(), ctx.getRegionId(), loadBalancerId).execute();
            method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");

            //removeLoadBalancerHealthCheck(loadBalancerId);
        } catch (CloudException e) {
            throw new CloudException(e);
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

    public String getLoadBalancerHealthCheckName(@Nonnull String loadBalancerId) throws CloudException, InternalException {
        gce = provider.getGoogleCompute();
        TargetPool tp;
        try {
            tp = gce.targetPools().get(ctx.getAccountNumber(), ctx.getRegionId(), loadBalancerId).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }

        String healthCheck = null;
        if (tp != null) {
            List<String> hcs = tp.getHealthChecks();
            if ((hcs != null) && (hcs.size() > 0)) {
                healthCheck  = hcs.get(0);
                healthCheck = healthCheck.substring(healthCheck.lastIndexOf("/") + 1);
            }
        }
        return healthCheck;
    }

    private List<String> getForwardingRules(String targetPoolName) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.getForwardingRule");
        gce = provider.getGoogleCompute();

        List<String> forwardingRuleNames = new ArrayList<String>();
        try {
            ForwardingRuleList result = gce.forwardingRules().list(ctx.getAccountNumber(), ctx.getRegionId()).execute();
            if ((result != null) && (result.getItems() != null))
                for (ForwardingRule fr : result.getItems()) {
                    String forwardingRuleTarget = fr.getTarget();
                    forwardingRuleTarget = forwardingRuleTarget.substring(forwardingRuleTarget.lastIndexOf("/") + 1);

                    if (targetPoolName.equals(forwardingRuleTarget)) 
                        forwardingRuleNames.add(fr.getName());
                    }
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } finally {
            APITrace.end();
        }
        return forwardingRuleNames;
    }

    private int flatten(@Nonnull String portRange) throws CloudException {
        String[] ports = portRange.split("-");
        if (ports[0].equals(ports[1]))
            return new Integer(ports[0]);
        throw new CloudException("expected a port range like 80-80, got " + portRange);
    }

    private LbProtocol standardizeGCEProtocol(@Nonnull String protocol) {
        if (protocol.equals("TCP"))
            return LbProtocol.RAW_TCP;
        return LbProtocol.valueOf(protocol);
    }

    private boolean isIPAddressInUse(@Nonnull String ipAddress, HashMap<String, ForwardingRule> rules) {
        for (String ruleName : rules.keySet()) 
            if (rules.get(ruleName).getIPAddress().equals(ipAddress)) 
                return true;
        return false;
    }

    @Override
    public void removeListeners(String toLoadBalancerId, LbListener[] listeners) throws CloudException, InternalException {
        gce = provider.getGoogleCompute();
        ForwardingRule forwardingRule = null;
        List<String> existingForwardingRuleNames = getForwardingRules(toLoadBalancerId);
        try {
            HashMap<String, ForwardingRule> existingForwardingRules = new HashMap<String, ForwardingRule>();
            for (String ruleName : existingForwardingRuleNames) 
                existingForwardingRules.put(ruleName, gce.forwardingRules().get(ctx.getAccountNumber(), ctx.getRegionId(), ruleName).execute());

            for (String ruleName : existingForwardingRuleNames) {
                for (LbListener listener : listeners) {
                    forwardingRule = existingForwardingRules.get(ruleName);
                    if ( (forwardingRule != null) &&
                         (flatten(forwardingRule.getPortRange()) == listener.getPublicPort()) &&
                         (flatten(forwardingRule.getPortRange()) == listener.getPrivatePort()) &&
                         (standardizeGCEProtocol(forwardingRule.getIPProtocol()).equals(listener.getNetworkProtocol())) ) {

                        Operation job = gce.forwardingRules().delete(ctx.getAccountNumber(), ctx.getRegionId(), ruleName).execute();
                        GoogleMethod method = new GoogleMethod(provider);
                        method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");

                        existingForwardingRules.remove(ruleName); //NEW
                        if (isIPAddressInUse(forwardingRule.getIPAddress(), existingForwardingRules) == false) {
                            IPAddressSupport ipSupport = provider.getNetworkServices().getIpAddressSupport();
                            String ip = forwardingRule.getIPAddress();

                            if (ip != null)
                                ipSupport.releaseFromPool(ipSupport.getIpAddressIdFromIP(ip, provider.getContext().getRegionId()));
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
    }

    private void removeLoadBalancerForwardingRule(String forwardingRuleName) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.removeLoadBalancerForwardingRule");
        gce = provider.getGoogleCompute();

        try {
            Operation job = gce.forwardingRules().delete(ctx.getAccountNumber(), ctx.getRegionId(), forwardingRuleName).execute();

            GoogleMethod method = new GoogleMethod(provider);
            method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull String createLoadBalancer(@Nonnull LoadBalancerCreateOptions options) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.create");

        gce = provider.getGoogleCompute();
        try {
            TargetPool tp = new TargetPool();
            tp.setRegion(ctx.getRegionId());
            tp.setName(options.getName());
            tp.setInstances(null);

            try {
                GoogleMethod method = new GoogleMethod(provider);
                Operation job = gce.targetPools().insert(ctx.getAccountNumber(), ctx.getRegionId(), tp).execute();
                method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");
            } catch (IOException e) {
                if (e.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException(e);
            }
            HealthCheckOptions hco = options.getHealthCheckOptions();

            if (hco != null) {
                LoadBalancerHealthCheck hc = createLoadBalancerHealthCheck(hco.getName(), hco.getDescription(), hco.getHost(), hco.getProtocol(), hco.getPort(), hco.getPath(), hco.getInterval(), hco.getTimeout(), hco.getHealthyCount(), hco.getUnhealthyCount());
                attachHealthCheckToLoadBalancer(options.getName(), options.getHealthCheckOptions().getName());
            }

            createLoadBalancerForwardingRule(options);

            return options.getName();
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void addListeners(String toLoadBalancerId, LbListener[] listeners) throws CloudException, InternalException {
        gce = provider.getGoogleCompute();
        int index = 0;

        try {
            TargetPool tp = gce.targetPools().get(ctx.getAccountNumber(), ctx.getRegionId(), toLoadBalancerId).execute();
            if (tp == null)
                throw new CloudException("Target Pool " + toLoadBalancerId + " not found.");

            List<String> forwardingRuleNames = getForwardingRules(toLoadBalancerId);
            String ipAddress = null;
            for (String ruleName : forwardingRuleNames) {
                ForwardingRule forwardingRules = gce.forwardingRules().get(ctx.getAccountNumber(), ctx.getRegionId(), ruleName).execute();
                if (null == ipAddress)
                    ipAddress = forwardingRules.getIPAddress();
                String[] ruleNameComponents = ruleName.split("-");
                if (ruleNameComponents[ruleNameComponents.length - 1].matches("\\d+")) {
                    int num = new Integer(ruleNameComponents[ruleNameComponents.length -1]);

                    if (num >= index)
                        index = num + 1;
                }
            }

            if (ipAddress == null)
                ipAddress = provider.getNetworkServices().getIpAddressSupport().getIpAddress(provider.getNetworkServices().getIpAddressSupport().request(IPVersion.IPV4)).getRawAddress().getIpAddress();

            String targetPoolSelfLink = tp.getSelfLink();

            for ( LbListener listener : listeners) {
                ForwardingRule forwardingRule = new ForwardingRule();
                forwardingRule.setName(toLoadBalancerId + "-" + index++);
                forwardingRule.setDescription(toLoadBalancerId);
                forwardingRule.setIPAddress(ipAddress);
                forwardingRule.setIPProtocol("TCP");
                forwardingRule.setPortRange("" + listener.getPublicPort());
                forwardingRule.setRegion(ctx.getRegionId());
                forwardingRule.setTarget(targetPoolSelfLink);

                GoogleMethod method = new GoogleMethod(provider);
                Operation job = gce.forwardingRules().insert(ctx.getAccountNumber(), ctx.getRegionId(), forwardingRule).execute();
                method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");
            }
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception e) {
            throw new CloudException(e); // catch the exception from method.getOperation
        }
    }


    void createLoadBalancerForwardingRule(@Nonnull LoadBalancerCreateOptions options)  throws CloudException, InternalException {
        APITrace.begin(provider, "LB.createLoadBalancerForwardingRule");
        gce = provider.getGoogleCompute();

        LbListener[] listeners = options.getListeners();

        String targetPoolSelfLink = null;
        try {
            TargetPool tp = gce.targetPools().get(ctx.getAccountNumber(), ctx.getRegionId(), options.getName()).execute();
            if (tp == null)
                throw new CloudException("Target Pool " + options.getName() + " not found.");

            targetPoolSelfLink = tp.getSelfLink();

            // Create an IP address for the load balancer if it is not specified.
            String ipAddress = options.getProviderIpAddressId();
            if ((options.getProviderIpAddressId() == null) || (options.getProviderIpAddressId().equals(""))) 
                ipAddress = provider.getNetworkServices().getIpAddressSupport().getIpAddress(provider.getNetworkServices().getIpAddressSupport().request(IPVersion.IPV4)).getRawAddress().getIpAddress();

            if (listeners.length > 0) {
                // listeners specified
                int index = 0;
                for ( LbListener listener : listeners) {
                    ForwardingRule forwardingRule = new ForwardingRule();
                    if (listeners.length > 1)
                        forwardingRule.setName(options.getName() + "-" + index++);
                    else
                        forwardingRule.setName(options.getName());

                    forwardingRule.setDescription(options.getDescription());
                    //forwardingRule.setKind("compute#forwardingRule");
                    forwardingRule.setIPAddress(ipAddress);
                    forwardingRule.setIPProtocol("TCP");
                    forwardingRule.setPortRange("" + listener.getPublicPort());
                    forwardingRule.setRegion(ctx.getRegionId());
                    forwardingRule.setTarget(targetPoolSelfLink);

                    GoogleMethod method = new GoogleMethod(provider);
                    Operation job = gce.forwardingRules().insert(ctx.getAccountNumber(), ctx.getRegionId(), forwardingRule).execute();
                    method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");
                }
            } else {
                // no listeners specified, default to ephemeral, port 80, TCP
                ForwardingRule forwardingRule = new ForwardingRule();
                forwardingRule.setName(options.getName());
                forwardingRule.setDescription("Default Forwarding Rule");
                //forwardingRule.setKind("compute#forwardingRule");
                forwardingRule.setIPAddress(ipAddress);
                forwardingRule.setIPProtocol("TCP");
                forwardingRule.setPortRange( "80");
                forwardingRule.setRegion(ctx.getRegionId());
                forwardingRule.setTarget(targetPoolSelfLink);

                GoogleMethod method = new GoogleMethod(provider);
                Operation job = gce.forwardingRules().insert(ctx.getAccountNumber(), ctx.getRegionId(), forwardingRule).execute();
                method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");
            }
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception e) {
            throw new CloudException(e); // catch the exception from method.getOperation
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public LoadBalancerHealthCheck createLoadBalancerHealthCheck(@Nonnull HealthCheckOptions options) throws CloudException, InternalException{
        return createLoadBalancerHealthCheck(
                options.getName(),
                options.getDescription(),
                options.getHost(),
                LoadBalancerHealthCheck.HCProtocol.HTTP,
                options.getPort(),
                options.getPath(),
                options.getInterval(),
                options.getTimeout(),
                options.getHealthyCount(),
                options.getUnhealthyCount());
    }

    @Override
    public LoadBalancerHealthCheck createLoadBalancerHealthCheck(@Nullable String name, @Nullable String description, @Nullable String host, @Nullable LoadBalancerHealthCheck.HCProtocol protocol, int port, @Nullable String path, int interval, int timeout, int healthyCount, int unhealthyCount) throws CloudException, InternalException{
        APITrace.begin(provider, "LB.createLoadBalancerHealthCheck");
        gce = provider.getGoogleCompute();

        HttpHealthCheck hc = new HttpHealthCheck();

        try {
            hc.setName(name);
            hc.setDescription(description);
            hc.setHost(host);
            // protocol
            hc.setPort(port);
            hc.setRequestPath(path);
            hc.setCheckIntervalSec(interval);
            hc.setTimeoutSec(timeout);
            hc.setHealthyThreshold(healthyCount);
            hc.setUnhealthyThreshold(unhealthyCount);

            GoogleMethod method = new GoogleMethod(provider);
            Operation job = gce.httpHealthChecks().insert(ctx.getAccountNumber(), hc).execute();
            method.getOperationComplete(ctx, job, GoogleOperationType.GLOBAL_OPERATION, ctx.getRegionId(), "");
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
        return getLoadBalancerHealthCheck(name);
    }

    @Override
    public void attachHealthCheckToLoadBalancer(@Nonnull String providerLoadBalancerId, @Nonnull String providerLBHealthCheckId)throws CloudException, InternalException{
        APITrace.begin(provider, "LB.attachHealthCheckToLoadBalancer");
        gce = provider.getGoogleCompute();

        HttpHealthCheck hc = null;
        try {
            hc = (gce.httpHealthChecks().get(ctx.getAccountNumber(), providerLBHealthCheckId)).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }

        ArrayList <HealthCheckReference>hcl = new ArrayList<HealthCheckReference>();
        HealthCheckReference hcr = new HealthCheckReference();
        hcr.setHealthCheck(hc.getSelfLink());
        hcl.add(hcr);
        TargetPoolsAddHealthCheckRequest tphcr = new TargetPoolsAddHealthCheckRequest();
        tphcr.setHealthChecks(hcl);

        try {
            gce.targetPools().addHealthCheck(ctx.getAccountNumber(), ctx.getRegionId(), providerLoadBalancerId, tphcr).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

    public LoadBalancerHealthCheck toLoadBalancerHealthCheck(String loadBalancerName, HttpHealthCheck hc)  throws CloudException, InternalException {
        if (loadBalancerName == null)
            throw new InternalException("loadBalancerName was null. Name is required");

        if (hc == null)
            throw new InternalException("HttpHealthCheck was null");

        Integer port = -1;
        if (hc.getPort() != null)
            port = hc.getPort();

        Integer checkIntervalSecond = -1;
        if (hc.getCheckIntervalSec() != null)
            checkIntervalSecond = hc.getCheckIntervalSec();

        Integer timeoutSec = -1;
        if (hc.getTimeoutSec() != null)
            timeoutSec = hc.getTimeoutSec();

        Integer healthyThreshold = -1;
        if (hc.getHealthyThreshold() != null)
            healthyThreshold = hc.getHealthyThreshold();

        Integer unhealthyThreshold = -1;
        if (hc.getUnhealthyThreshold() != null)
            unhealthyThreshold = hc.getUnhealthyThreshold();

        LoadBalancerHealthCheck lbhc = LoadBalancerHealthCheck.getInstance(
                loadBalancerName, 
                hc.getName(),
                hc.getDescription(),
                hc.getHost(), 
                HCProtocol.TCP,
                port,
                hc.getRequestPath(), 
                checkIntervalSecond,
                timeoutSec,
                healthyThreshold,
                unhealthyThreshold);
        lbhc.addProviderLoadBalancerId(loadBalancerName);
        return lbhc;
    }

    /*
     * Inventory Load Balancers and list their associated Health Checks.
     * Caveat, will only show FIRST health check
     */
    @Override
    public Iterable<LoadBalancerHealthCheck> listLBHealthChecks(@Nullable HealthCheckFilterOptions opts) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.listLBHealthChecks");
        gce = provider.getGoogleCompute();

        ArrayList<LoadBalancerHealthCheck> lbhc = new ArrayList<LoadBalancerHealthCheck>();

        try {
            TargetPoolList tpl = gce.targetPools().list(ctx.getAccountNumber(), ctx.getRegionId()).execute();

            if (tpl.getItems() != null) {
                Iterator<TargetPool> loadBalancers = tpl.getItems().iterator();

                while (loadBalancers.hasNext()) {
                    TargetPool lb = loadBalancers.next();
                    String loadBalancerName = lb.getName();

                    List<String> hcs = lb.getHealthChecks();
                    if ((hcs != null) && (!hcs.isEmpty())) {
                        String healthCheckName = hcs.get(0);
                        if (healthCheckName != null) {
                            healthCheckName = healthCheckName.substring(healthCheckName.lastIndexOf("/") + 1);
                            HttpHealthCheck hc = gce.httpHealthChecks().get(ctx.getAccountNumber(), healthCheckName).execute();
                            if (hc != null) {
                                LoadBalancerHealthCheck healthCheckItem = toLoadBalancerHealthCheck(loadBalancerName, hc);
                                lbhc.add(healthCheckItem);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
        return lbhc;
    }

    @Override
    public void detatchHealthCheck(@Nonnull String loadBalancerId, @Nonnull String heathcheckId) throws CloudException, InternalException{
        APITrace.begin(provider, "LB.detatchHealthCheck");
        gce = provider.getGoogleCompute();

        try {
            TargetPoolsRemoveHealthCheckRequest request = new TargetPoolsRemoveHealthCheckRequest();
            List<HealthCheckReference> healthCheckList = new ArrayList<HealthCheckReference>();
            HealthCheckReference healthCheckReference = new HealthCheckReference();
            HttpHealthCheck hc = gce.httpHealthChecks().get(ctx.getAccountNumber(), heathcheckId).execute(); 
            healthCheckReference.setHealthCheck(hc.getSelfLink()); 
            healthCheckList.add(healthCheckReference );
            request.setHealthChecks(healthCheckList );
            gce.targetPools().removeHealthCheck(ctx.getAccountNumber(), ctx.getRegionId(), loadBalancerId, request).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
	public void removeLoadBalancerHealthCheck(String healthCheckId) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.removeLoadBalancerHealthCheck");
        gce = provider.getGoogleCompute();
        try {
            Operation job = gce.httpHealthChecks().delete(ctx.getAccountNumber(), healthCheckId).execute();
            GoogleMethod method = new GoogleMethod(provider);
            boolean result = method.getOperationComplete(ctx, job, GoogleOperationType.GLOBAL_OPERATION, ctx.getRegionId(), "");  // Causes CloudException if HC still in use.
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } finally {
            APITrace.end();
        }
    }

    @Override
    public LoadBalancerHealthCheck modifyHealthCheck(@Nonnull String providerLBHealthCheckId, @Nonnull HealthCheckOptions options) throws InternalException, CloudException{
        APITrace.begin(provider, "LB.modifyHealthCheck");
        gce = provider.getGoogleCompute();

        HttpHealthCheck hc = null;
        try {
            hc = (gce.httpHealthChecks().get(ctx.getAccountNumber(), providerLBHealthCheckId)).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }

        if (options.getName() != null)
            hc.setName(options.getName()); // Cannot set name to null 
        hc.setDescription(options.getDescription());
        hc.setHost(options.getHost());
        hc.setRequestPath(options.getPath());
        // TODO: Is protocol to be supported?
        hc.setPort(options.getPort());
        hc.setCheckIntervalSec(options.getInterval());
        hc.setTimeoutSec(options.getTimeout());
        hc.setHealthyThreshold(options.getHealthyCount());
        hc.setUnhealthyThreshold(options.getUnhealthyCount());

        try {
            gce.httpHealthChecks().update(ctx.getAccountNumber(), providerLBHealthCheckId, hc).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
        return getLoadBalancerHealthCheck(providerLBHealthCheckId);
    }

    @Override
    public LoadBalancerHealthCheck getLoadBalancerHealthCheck(@Nonnull String providerLBHealthCheckId, @Nullable String providerLoadBalancerId)throws CloudException, InternalException{
        return getLoadBalancerHealthCheck(providerLBHealthCheckId);
    }

    private LoadBalancerHealthCheck getLoadBalancerHealthCheck(@Nonnull String providerLBHealthCheckId)throws CloudException, InternalException{
        APITrace.begin(provider, "LB.getLoadBalancerHealthCheck");
        gce = provider.getGoogleCompute();

        HttpHealthCheck hc = null;
        LoadBalancerHealthCheck lbhc = null;
        try {
            hc = (gce.httpHealthChecks().get(ctx.getAccountNumber(), providerLBHealthCheckId)).execute();

            lbhc = toLoadBalancerHealthCheck(providerLBHealthCheckId, hc);
            //lbhc.addProviderLoadBalancerId(hc.getName());
        } catch (NullPointerException e) {
            // not found, return null
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
        return lbhc;
    }

    @Override
    public @Nullable LoadBalancer getLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.getLoadBalancer");
        gce = provider.getGoogleCompute();

        LoadBalancer lb = null;
        try {
            TargetPool tp = gce.targetPools().get(ctx.getAccountNumber(), ctx.getRegionId(), loadBalancerId).execute();
            lb = toLoadBalancer(tp);
        } catch (Exception e) {
            lb = null;
        }
        finally {
            APITrace.end();
        }
        return lb;
    }

    @Override
    public void addServers(@Nonnull String toLoadBalancerId, @Nonnull String ... serverIdsToAdd) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.addServers");
        gce = provider.getGoogleCompute();
        String vmRegion = null;
        try {
            List<InstanceReference> instances = new ArrayList<InstanceReference>();
            for (String server : serverIdsToAdd) {
                VirtualMachine vm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(server);
                vmRegion = vm.getProviderRegionId();
                instances.add(new InstanceReference().setInstance((String) vm.getTag("contentLink")));
            }

            gce.targetPools().addInstance(ctx.getAccountNumber(), vmRegion, toLoadBalancerId, new TargetPoolsAddInstanceRequest().setInstances(instances)).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void removeServers(@Nonnull String fromLoadBalancerId, @Nonnull String ... serverIdsToRemove) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.removeServers");
        gce = provider.getGoogleCompute();

        List<InstanceReference> replacementInstances = new ArrayList<InstanceReference>();
        try {
            TargetPool tp = gce.targetPools().get(ctx.getAccountNumber(), ctx.getRegionId(), fromLoadBalancerId).execute();
            if (tp == null)
                throw new CloudException("Target Pool " + fromLoadBalancerId + " not found.");
            List<String> instances = tp.getInstances();

            for (String i : instances)
                for (String serverToRemove : serverIdsToRemove) 
                    if (i.endsWith(serverToRemove))
                        replacementInstances.add(new InstanceReference().setInstance(i));

            TargetPoolsRemoveInstanceRequest content = new TargetPoolsRemoveInstanceRequest();
            content.setInstances(replacementInstances);
            gce.targetPools().removeInstance(ctx.getAccountNumber(), ctx.getRegionId(), fromLoadBalancerId, content).execute();

        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<LoadBalancerEndpoint> listEndpoints(@Nonnull String forLoadBalancerId) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.listEndpoints");
        gce = provider.getGoogleCompute();

        TargetPool tp = null;
        try {
            tp = gce.targetPools().get(ctx.getAccountNumber(), ctx.getRegionId(), forLoadBalancerId).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }

        if (tp == null)
            throw new CloudException("Target Pool " + forLoadBalancerId + " not found.");

        try {
            ArrayList<LoadBalancerEndpoint> list = new ArrayList<LoadBalancerEndpoint>();
            List<String> instances = tp.getInstances();
            if (instances != null)
                for (String instance : instances) 
                    list.add(LoadBalancerEndpoint.getInstance(LbEndpointType.VM, instance.substring(1 + instance.lastIndexOf("/")), LbEndpointState.ACTIVE));

            return list;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listLoadBalancerStatus() throws CloudException, InternalException {

        APITrace.begin(provider, "LB.listLoadBalancers");
        gce = provider.getGoogleCompute();
        ArrayList<ResourceStatus> list = new ArrayList<ResourceStatus>();

        try {
            TargetPoolList tpl = gce.targetPools().list(ctx.getAccountNumber(), ctx.getRegionId()).execute();
            if ((tpl != null) && (tpl.getItems() != null)) { 
                Iterator<TargetPool> loadBalancers = tpl.getItems().iterator();

                while (loadBalancers.hasNext()) {
                    TargetPool lb = loadBalancers.next();
                    List<String> healthChecks = lb.getHealthChecks();
                    for (String healthCheckName : healthChecks) {
                        healthCheckName = healthCheckName.substring(healthCheckName.lastIndexOf("/") + 1);
                        LoadBalancerHealthCheck healthCheck = getLoadBalancerHealthCheck(healthCheckName);
                        list.add(new ResourceStatus(lb.getName(), "UNKNOWN"));
                    }
                }
            }
            return list;
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<LoadBalancer> listLoadBalancers() throws CloudException, InternalException {
        APITrace.begin(provider, "LB.listLoadBalancers");
        gce = provider.getGoogleCompute();
        ArrayList<LoadBalancer> list = new ArrayList<LoadBalancer>();
        try {
            if (null == ctx.getAccountNumber())
                throw new InternalException("Account number cannot be null");
            if (null == ctx.getRegionId())
                throw new InternalException("RegionId cannot be null");
            TargetPoolList tpl = gce.targetPools().list(ctx.getAccountNumber(), ctx.getRegionId()).execute();

            if ((tpl != null) && (tpl.getItems() != null)) { 
                Iterator<TargetPool> loadBalancers = tpl.getItems().iterator();

                while (loadBalancers.hasNext()) {
                    TargetPool lb = loadBalancers.next();
                    LoadBalancer loadBalancer = toLoadBalancer(lb);
                    if( loadBalancer != null ) {
                        list.add(loadBalancer);
                    }
                }
            }
            return list;
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
    }

    private LoadBalancer toLoadBalancer(TargetPool tp) throws CloudException, InternalException {
        gce = provider.getGoogleCompute();
        List<String> hcl = tp.getHealthChecks();
        String healthCheckName = null;
        if ((hcl != null) && (!hcl.isEmpty())) {
            healthCheckName = hcl.get(0);
            healthCheckName = healthCheckName.substring(healthCheckName.lastIndexOf("/") + 1);
        }

        long created = 0;
        try {
            created = provider.parseTime(tp.getCreationTimestamp());
        } catch (CloudException e) {
            throw new CloudException(e);
        }
        ForwardingRule fr = null;
        String forwardingRuleAddress = null;
        String forwardingRulePortRange = null;
        int ports[] = null;
        List<LbListener> listeners = new ArrayList<LbListener>();
        try {
            List<String> forwardingRuleNames = getForwardingRules(tp.getName());
            for (String forwardingRuleName : forwardingRuleNames) {
                fr = gce.forwardingRules().get(ctx.getAccountNumber(), ctx.getRegionId(), forwardingRuleName).execute();
                if (fr != null) {
                    forwardingRuleAddress = fr.getIPAddress();
                    forwardingRulePortRange = fr.getPortRange();
                    ports = portsToRange(forwardingRulePortRange);
                    for (int port : ports) 
                        // Hard Coded Algorithm and persistence, havent found a dynamic source yet.
                        listeners.add(LbListener.getInstance(LbAlgorithm.SOURCE, LbPersistence.SUBNET, standardizeGCEProtocol(fr.getIPProtocol()), port, port));
                }
            }
        } catch (IOException e) {
            // Guess no forwarding rules for this one.
        }

        String region = tp.getRegion();
        region = region.substring(region.lastIndexOf("/") + 1);

        List<String> zones = new ArrayList<String>();
        try {
            Region puzzle = gce.regions().get(ctx.getAccountNumber(), ctx.getRegionId()).execute();
            List<String> longZones = puzzle.getZones();

            for( String zone : longZones ) {
                zone = zone.substring(zone.lastIndexOf("/") + 1);
                zones.add(zone);
            }
        }
        catch( Throwable ignore ) {

        }

        String dataCenterIDs[] = new String[zones.size()];
        dataCenterIDs = zones.toArray(dataCenterIDs);
        LoadBalancer lb = LoadBalancer.getInstance(
                ctx.getAccountNumber(), 
                region, 
                tp.getName(), 
                LoadBalancerState.ACTIVE, 
                tp.getName(), 
                tp.getDescription(), 
                LbType.EXTERNAL,
                LoadBalancerAddressType.DNS,
                forwardingRuleAddress,
                healthCheckName, // TODO: need to modify setProviderLBHealthCheckId to accept lists or arrays
                ports).operatingIn(dataCenterIDs).supportingTraffic(IPVersion.IPV4).createdAt(created);

        LbListener LBListeners[] = new LbListener[listeners.size()];
        LBListeners = listeners.toArray(LBListeners);
        if (!listeners.isEmpty())
            lb = lb.withListeners(LBListeners);
        return lb;
        }

    private int[] portsToRange(String portRange) {
        int[] ports;
        if (portRange.contains("-")) {
            String[] parts = portRange.split("-");
            int start = new Integer(parts[0]);
            int end = new Integer(parts[1]);
            ports = new int[(end - start) + 1];
            for (int x = 0; x< (end - start) + 1; x++) {
                ports[x] = start + x;
            }
        } else
            ports = new int[]{new Integer(portRange)};

        return ports;
    }
}
