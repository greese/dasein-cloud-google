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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCELoadBalancerCapabilities;
import org.dasein.cloud.network.AbstractLoadBalancerSupport;
import org.dasein.cloud.network.HealthCheckFilterOptions;
import org.dasein.cloud.network.HealthCheckOptions;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.LbEndpointState;
import org.dasein.cloud.network.LbEndpointType;
import org.dasein.cloud.network.LbListener;
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
import com.google.api.services.compute.Compute.TargetPools.AddHealthCheck;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.ForwardingRuleList;
import com.google.api.services.compute.model.HealthCheckReference;
import com.google.api.services.compute.model.HttpHealthCheck;
import com.google.api.services.compute.model.InstanceReference;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.TargetPool;
import com.google.api.services.compute.model.TargetPoolList;
import com.google.api.services.compute.model.TargetPoolsAddHealthCheckRequest;
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest;
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
    public @Nonnull Requirement identifyEndpointsOnCreateRequirement() throws CloudException, InternalException {
    	return Requirement.OPTIONAL;
    }

    @Override
    public @Nonnull Requirement identifyListenersOnCreateRequirement() throws CloudException, InternalException {
    	return Requirement.OPTIONAL;
    }

    @Override
    public @Nonnull Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return Collections.singletonList(IPVersion.IPV4);
    }

    @Override
    public boolean isDataCenterLimited() {
    	return false;
    }

    @Nonnull
    @Override
    public LoadBalancerCapabilities getCapabilities() throws CloudException, InternalException {
        if( capabilities == null ) {
        	capabilities = new GCELoadBalancerCapabilities(provider);
        }
        return capabilities;
    }

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		// TODO need to understand correct value for here.
		return true;
	}

    @Override
    public void removeLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {
    	APITrace.begin(provider, "LB.removeLoadBalancer");

		gce = provider.getGoogleCompute();

    	removeLoadBalancerForwardingRule(loadBalancerId); 

        try {
        	GoogleMethod method = new GoogleMethod(provider);
        	Operation job = gce.targetPools().delete(ctx.getAccountNumber(), ctx.getRegionId(), loadBalancerId).execute();
        	boolean result = method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");

	        //removeLoadBalancerHealthCheck(loadBalancerId);
        } catch (CloudException e) {
        	throw new CloudException(e);
		} catch (IOException e) {
			throw new InternalException(e);
		}
        finally {
            APITrace.end();
        }
    }

    private void removeLoadBalancerForwardingRule(String forwardingRuleName) throws CloudException, InternalException {
    	APITrace.begin(provider, "LB.removeLoadBalancerForwardingRule");
        gce = provider.getGoogleCompute();

    	try {
			Operation result = gce.forwardingRules().delete(ctx.getAccountNumber(), ctx.getRegionId(), forwardingRuleName).execute();
		} catch (IOException e) {
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
	        	boolean result = method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");
   			} catch (IOException e) {
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

    void createLoadBalancerForwardingRule(@Nonnull LoadBalancerCreateOptions options)  throws CloudException, InternalException {
    	APITrace.begin(provider, "LB.createLoadBalancerForwardingRule");
        gce = provider.getGoogleCompute();

        LbListener[] listeners = options.getListeners();

        String targetPoolSelfLink = null;
        try {
        	TargetPool tp = gce.targetPools().get(ctx.getAccountNumber(), ctx.getRegionId(), options.getName()).execute();

        	targetPoolSelfLink  = tp.getSelfLink();

	    	if (listeners.length > 0) {
	    		// listeners specified
	    		int index = 0;
	    		for ( LbListener listener : listeners) {
	    			ForwardingRule forwardingRule = new ForwardingRule();
	    			if (listeners.length > 0)
	    				forwardingRule.setName(options.getName() + "-" + index++);
	    			else
	    				forwardingRule.setName(options.getName());

	    			forwardingRule.setDescription(options.getDescription());
	    			//forwardingRule.setKind("compute#forwardingRule");
	    			forwardingRule.setIPAddress(options.getProviderIpAddressId());
	    			forwardingRule.setIPProtocol("TCP");
	    			forwardingRule.setPortRange("" + listener.getPublicPort());
	    			forwardingRule.setRegion(ctx.getRegionId());
	    			forwardingRule.setTarget(targetPoolSelfLink);

					Operation result = gce.forwardingRules().insert(ctx.getAccountNumber(), ctx.getRegionId(), forwardingRule).execute();
	    		}
	    	} else {
	    		// no listeners specified, default to ephemeral, all ports, TCP
				ForwardingRule forwardingRule = new ForwardingRule();
				forwardingRule.setName(options.getName());
				forwardingRule.setDescription("Default Forwarding Rule");
				//forwardingRule.setKind("compute#forwardingRule");
				//forwardingRule.setIPAddress("");
				forwardingRule.setIPProtocol("TCP");
				forwardingRule.setPortRange( "1-65535");
				forwardingRule.setRegion(ctx.getRegionId());
				forwardingRule.setTarget(targetPoolSelfLink);

				GoogleMethod method = new GoogleMethod(provider);
	            Operation job = gce.forwardingRules().insert(ctx.getAccountNumber(), ctx.getRegionId(), forwardingRule).execute();
	            boolean result = method.getOperationComplete(ctx, job, GoogleOperationType.REGION_OPERATION, ctx.getRegionId(), "");
	    	}
	    } catch (IOException e) {
	       	throw new CloudException(e);
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
        		LoadBalancerHealthCheck.HCProtocol.TCP,
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
        	boolean result = method.getOperationComplete(ctx, job, GoogleOperationType.GLOBAL_OPERATION, ctx.getRegionId(), "");
		} catch (IOException e) {
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
			throw new CloudException(e);
		}

        ArrayList <HealthCheckReference>hcl = new ArrayList<HealthCheckReference>();
        HealthCheckReference hcr = new HealthCheckReference();
        hcr.setHealthCheck(hc.getSelfLink());
        hcl.add(hcr);
        TargetPoolsAddHealthCheckRequest tphcr = new TargetPoolsAddHealthCheckRequest();
        tphcr.setHealthChecks(hcl);

    	try {
		    AddHealthCheck op = gce.targetPools().addHealthCheck(ctx.getAccountNumber(), ctx.getRegionId(), providerLoadBalancerId, tphcr);
			Operation result = op.execute();
		} catch (IOException e) {
        	throw new CloudException(e);
		}
        finally {
            APITrace.end();
        }
    }



    public LoadBalancerHealthCheck toLoadBalancerHealthCheck(String loadBalancerName, HttpHealthCheck hc) {
		return LoadBalancerHealthCheck.getInstance(
				loadBalancerName, 
    			hc.getName(),
    			hc.getDescription(),
    			hc.getHost(), 
    			HCProtocol.TCP,
    			hc.getPort(),
    			hc.getRequestPath(), 
    			hc.getCheckIntervalSec(), 
    			hc.getTimeoutSec(),
    			hc.getUnhealthyThreshold(),
    			hc.getHealthyThreshold());
    }

	/*
	 * Inventory Load Balancers and list their associated Health Checks.
	 * Caveat, will only show FIRST health check
	 */
    @Override
    public Iterable<LoadBalancerHealthCheck> listLBHealthChecks(@Nullable HealthCheckFilterOptions opts) throws CloudException, InternalException{
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
					String healthCheckName = lb.getHealthChecks().get(0);
					HttpHealthCheck hc = gce.httpHealthChecks().get(ctx.getAccountNumber(), healthCheckName).execute();

					LoadBalancerHealthCheck healthCheckItem = toLoadBalancerHealthCheck(loadBalancerName, hc);

					lbhc.add(healthCheckItem);
				}
    		}
		} catch (IOException e) {
			throw new CloudException(e);
		}
        finally {
            APITrace.end();
        }
    	return lbhc;
    }

    @Override
    public void removeLoadBalancerHealthCheck(@Nonnull String providerLoadBalancerId) throws CloudException, InternalException{
    	APITrace.begin(provider, "LB.removeLoadBalancerHealthCheck");
        gce = provider.getGoogleCompute();

		try {
			Operation op = (gce.httpHealthChecks().delete(ctx.getAccountNumber(), providerLoadBalancerId)).execute();
		} catch (IOException e) {
			throw new CloudException(e);
		}
        finally {
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
			Operation op = gce.httpHealthChecks().update(ctx.getAccountNumber(), providerLBHealthCheckId, hc).execute();
		} catch (IOException e) {
			throw new CloudException(e);
		}
        finally {
            APITrace.end();
        }
    	return getLoadBalancerHealthCheck(providerLBHealthCheckId);
    }

    @Override
    public LoadBalancerHealthCheck getLoadBalancerHealthCheck(@Nullable String providerLBHealthCheckId, @Nullable String providerLoadBalancerId)throws CloudException, InternalException{
    	return getLoadBalancerHealthCheck(providerLBHealthCheckId);
    }

    public LoadBalancerHealthCheck getLoadBalancerHealthCheck(@Nullable String providerLBHealthCheckId)throws CloudException, InternalException{
    	APITrace.begin(provider, "LB.getLoadBalancerHealthCheck");
        gce = provider.getGoogleCompute();

    	HttpHealthCheck hc = null;
    	LoadBalancerHealthCheck lbhc = null;
    	try {
			hc = (gce.httpHealthChecks().get(ctx.getAccountNumber(), providerLBHealthCheckId)).execute();

	    	lbhc = toLoadBalancerHealthCheck(providerLBHealthCheckId, hc);
	    	lbhc.addProviderLoadBalancerId(hc.getName());
		} catch (IOException e) {
			// not found, return null
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
		} catch (IOException e) { 
			// not found, return null
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

    	try {
	    	List<InstanceReference> instances = new ArrayList<InstanceReference>();
    		for (String server : serverIdsToAdd) {
    			VirtualMachine vm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(server);
    			instances.add(new InstanceReference().setInstance((String) vm.getTag("contentLink")));
    		}

	    	gce.targetPools().addInstance(ctx.getAccountNumber(), ctx.getRegionId(), toLoadBalancerId, new TargetPoolsAddInstanceRequest().setInstances(instances)).execute();
		} catch (IOException e) {
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
			List<String> instances = tp.getInstances();

			for (String i : instances)
				for (String serverToRemove : serverIdsToRemove) 
					if (i.endsWith(serverToRemove))
						replacementInstances.add(new InstanceReference().setInstance(i));

	    	TargetPoolsRemoveInstanceRequest content = new TargetPoolsRemoveInstanceRequest();
	    	content.setInstances(replacementInstances);
			gce.targetPools().removeInstance(ctx.getAccountNumber(), ctx.getRegionId(), fromLoadBalancerId, content).execute();

		} catch (IOException e) {
	        throw new CloudException(e);
		}
    	finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<LoadBalancerEndpoint> listEndpoints(@Nonnull String forLoadBalancerId) throws CloudException, InternalException {
    	// see getLoadBalancerForwardingRuleAddress
    	// endpoints are inside portion of LB, i.e. the VM's
    	APITrace.begin(provider, "LB.listEndpoints");
        gce = provider.getGoogleCompute();

    	TargetPool tp = null;
    	try {
			tp = gce.targetPools().get(ctx.getAccountNumber(), ctx.getRegionId(), forLoadBalancerId).execute();
		} catch (IOException e) {
	        throw new CloudException(e);
		}

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
    public @Nonnull Iterable<LoadBalancer> listLoadBalancers() throws CloudException, InternalException {
    	APITrace.begin(provider, "LB.listLoadBalancers");
    	gce = provider.getGoogleCompute();
    	ArrayList<LoadBalancer> list = new ArrayList<LoadBalancer>();
    	try {
    		TargetPoolList tpl = gce.targetPools().list(ctx.getAccountNumber(), ctx.getRegionId()).execute();
    		List<TargetPool> x = tpl.getItems();
			if (tpl.getItems() != null) { 
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

    	String address = getLoadBalancerForwardingRuleAddress(ctx.getAccountNumber(), ctx.getRegionId());
    	String region = tp.getRegion();
    	region = region.substring(region.lastIndexOf("/") + 1);
    	return LoadBalancer.getInstance(
    				ctx.getAccountNumber(), 
					region, 
					tp.getName(), 
					LoadBalancerState.ACTIVE, 
					tp.getName(), 
					tp.getDescription(), 
					null,
					LoadBalancerAddressType.DNS,
					address,
					healthCheckName, // TODO: need to modify setProviderLBHealthCheckId to accept lists or arrays
					0//ports
	    		).supportingTraffic(IPVersion.IPV4).createdAt(created); // .withListeners(listeners) to add in listeners
	}

	private String getLoadBalancerForwardingRuleAddress(String accountNumber, String regionId) throws CloudException {
		ForwardingRuleList frl = null;
		try {
			frl = gce.forwardingRules().list(ctx.getAccountNumber(), ctx.getRegionId()).execute();
		} catch (IOException e) {
			if (e.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException(e);
		}
		Iterator<ForwardingRule> rules = frl.getItems().iterator();

		while (rules.hasNext()) {
 			ForwardingRule rule = rules.next();
			return rule.getIPAddress(); // return just the first address found 
		}
		return null;
	}

    @Override
    public @Nonnull Iterable<ResourceStatus> listLoadBalancerStatus() throws CloudException, InternalException {
    	throw new OperationNotSupportedException("LoadBalancerSupport.listLoadBalancerStatus  NOT IMPLEMENTED");
    }
}
