/**
 * Copyright (C) 2009-2014 Dell, Inc.
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
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.capabilities.GCELoadBalancerCapabilities;
import org.dasein.cloud.network.AbstractLoadBalancerSupport;
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
import org.dasein.cloud.util.APITrace;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.InstanceReference;
import com.google.api.services.compute.model.TargetPool;
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

	private TargetPool result2;
	public LoadBalancerSupport(Google provider) {
		super(provider);
        this.provider = provider;
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
	public String getProviderTermForLoadBalancer(Locale locale) {
		return "target pool";
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return false;
	}

    @Override
    public void removeLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
    	Compute gce = provider.getGoogleCompute();
    	
        try {
		    gce.targetPools().delete(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), loadBalancerId).execute();
        } catch (IOException e) {
        	throw new CloudException(e);
		}
        
        // make this operation blocking
        while (getLoadBalancer(loadBalancerId) != null) 
        	try {
        		System.out.println("Sleeping, waiting for LoadBalancer to show as deleted");
				Thread.sleep(500);
			} catch (InterruptedException e) { }
        
    }
	
    @Override
    public @Nonnull String createLoadBalancer(@Nonnull LoadBalancerCreateOptions options) throws CloudException, InternalException {
    	APITrace.begin(provider, "LB.create");

    	try {
            ProviderContext ctx = provider.getContext();
            if( ctx == null ) {
                throw new CloudException("No valid context is established for this request");
            }
            
        	LbListener[] listeners = options.getListeners();
        
            Compute gce = provider.getGoogleCompute();
            TargetPool tp = new TargetPool();
            tp.setRegion(ctx.getRegionId());
            tp.setName(options.getName());
            tp.setInstances(null);

            try {
   				 gce.targetPools().insert(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), tp).execute();
   			} catch (IOException e) {
   	        	throw new CloudException(e);
   			}
        	
            // make this operation blocking
            while (getLoadBalancer(options.getName()) == null) 
            	try {
            		System.out.println("Sleeping, waiting for LoadBalancer to come up");
    				Thread.sleep(500);
    			} catch (InterruptedException e) { }
            
        	return options.getName();
    	}	
        finally {
            APITrace.end();
        }
    }  

    public @Nullable LoadBalancer getLoadBalancer(@Nonnull String loadBalancerId) throws CloudException, InternalException {

        ProviderContext ctx = provider.getContext();
        
    	Compute gce = provider.getGoogleCompute();
    	LoadBalancer lb = null;
        try {
		    TargetPool tp = gce.targetPools().get(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), loadBalancerId).execute();
		    long created = provider.parseTime(tp.getCreationTimestamp());
		    lb = LoadBalancer.getInstance(
		    		ctx.getEffectiveAccountNumber(), 
		    		tp.getRegion(),
		    		tp.getName(), // GCE LB doesn't work with id for deletes.
		    		null, // LoadBalancerState - not supported by GCE targetpools
					tp.getName(), 
	    			tp.getDescription(), 
	    			LoadBalancerAddressType.DNS,
	    			null, //cname, address
	    			null //ports
	    			).supportingTraffic(IPVersion.IPV4).createdAt(created);
		} catch (IOException e) {
	        throw new CloudException(e);
		}
    	return lb;
    }

    public void addServers(@Nonnull String toLoadBalancerId, @Nonnull String ... serverIdsToAdd) throws CloudException, InternalException {
		// TODO: need to auto-set region, rather than hard code it.
    	addServers(toLoadBalancerId, "us-central1-b", serverIdsToAdd);
    }
    
    public void addServers(@Nonnull String toLoadBalancerId, @Nonnull String dataCenterId, @Nonnull String ... serverIdsToAdd) throws CloudException, InternalException {
    	ProviderContext ctx = provider.getContext();
    	Compute gce = provider.getGoogleCompute();

    	try {
	    	List<InstanceReference> instances = new ArrayList<InstanceReference>();
    		for (String server : serverIdsToAdd) {
    			String s = gce.getBaseUrl() + ctx.getEffectiveAccountNumber() + "/zones/" + dataCenterId + "/instances/" + server;
    			instances.add(new InstanceReference().setInstance(s));
    		}

	    	gce.targetPools().addInstance(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), toLoadBalancerId, new TargetPoolsAddInstanceRequest().setInstances(instances)).execute();
		} catch (IOException e) {
	        throw new CloudException(e);
		}
    }

    public void removeServers(@Nonnull String fromLoadBalancerId, @Nonnull String ... serverIdsToRemove) throws CloudException, InternalException {
    	ProviderContext ctx = provider.getContext();
    	Compute gce = provider.getGoogleCompute();

		List<InstanceReference> replacementInstances = new ArrayList<InstanceReference>();
    	try {
			TargetPool tp = gce.targetPools().get(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), fromLoadBalancerId).execute();
			List<String> instances = tp.getInstances();
			
			for (String i : instances) 
				for (String serverToRemove : serverIdsToRemove) 
					if (i.endsWith(serverToRemove))
						replacementInstances.add(new InstanceReference().setInstance(i));
			
	    	TargetPoolsRemoveInstanceRequest content = new TargetPoolsRemoveInstanceRequest();
	    	content.setInstances(replacementInstances);
			gce.targetPools().removeInstance(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), fromLoadBalancerId, content).execute();

		} catch (IOException e) {
	        throw new CloudException(e);
		}
    }
    
    @Override
    public @Nonnull Iterable<LoadBalancerEndpoint> listEndpoints(@Nonnull String forLoadBalancerId) throws CloudException, InternalException {
        APITrace.begin(provider, "LB.listEndpoints");

        ProviderContext ctx = provider.getContext();
    	Compute gce = provider.getGoogleCompute();
    	
    	TargetPool tp = null;
    	try {
			tp = gce.targetPools().get(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), forLoadBalancerId).execute();
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

    /*
     * This is for adding ip's to load balancer, not vm's ip's, but rather dedicated ips
     * 
    public void addIPEndpoints(@Nonnull String toLoadBalancerId, @Nonnull String ... ipAddresses) throws CloudException, InternalException {
    	System.out.println("in addIPEndpoints");
        ProviderContext ctx = provider.getContext();
    	Compute gce = provider.getGoogleCompute();
    	
    	TargetPool tp = null;
    	try {
			tp = gce.targetPools().get(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), toLoadBalancerId).execute();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	
    	int index = 1;
    	for (String ipAddress: ipAddresses) {
	    	ForwardingRule content = new ForwardingRule();
	    	System.out.println(" --> " + ipAddress);
	    	// Value of the reserved IP address that this forwarding rule is serving on behalf of. 
	    	// The address resource must live in the same region as the forwarding rule. 
	    	// If left empty (default value), an ephemeral IP will be assigned.
	    	content.setIPAddress(ipAddress); // "162.222.179.154"
	    	content.setTarget(tp.getSelfLink());
	    	content.setName(tp.getName() + "-" + index++);
	    	content.setRegion(ctx.getRegionId());
	    	String iPProtocol = "TCP";
			content.setIPProtocol(iPProtocol);
			content.setPortRange("10000-65535");
			try {
				Operation result = gce.forwardingRules().insert(ctx.getEffectiveAccountNumber(), ctx.getRegionId(), content).execute();
				System.out.println("here i am");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	
    }
    */

    public @Nonnull Iterable<LoadBalancerEndpoint> listEndpoints(@Nonnull String forLoadBalancerId, @Nonnull LbEndpointType type, @Nonnull String ... endpoints) throws CloudException, InternalException {
    	throw new OperationNotSupportedException("LoadBalancerSupport.listEndpoints  NOT IMPLEMENTED");
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listLoadBalancerStatus() throws CloudException, InternalException {
    	throw new OperationNotSupportedException("LoadBalancerSupport.listLoadBalancerStatus  NOT IMPLEMENTED");
    }

}
