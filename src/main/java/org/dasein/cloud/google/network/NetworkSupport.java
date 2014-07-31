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
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;

import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCENetworkCapabilities;
import org.dasein.cloud.network.*;
import org.dasein.cloud.network.NetworkInterface;
import org.dasein.cloud.network.Route;
import org.dasein.cloud.util.APITrace;

/**
 * Implements the network services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class NetworkSupport extends AbstractVLANSupport {

	static private final Logger logger = Google.getLogger(NetworkSupport.class);
	private Google provider;

	NetworkSupport(Google provider) {
        super(provider);
        this.provider = provider;
    }

	@Override
	public Route addRouteToAddress(String toRoutingTableId, IPVersion version, String destinationCidr, String address) throws CloudException, InternalException {
		throw new OperationNotSupportedException("GCE currently only supports routing to instances.");
	}

	@Override
	public Route addRouteToGateway(String toRoutingTableId, IPVersion version, String destinationCidr, String gatewayId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("GCE currently only supports routing to instances.");
	}

	@Override
	public Route addRouteToNetworkInterface(String toRoutingTableId, IPVersion version, String destinationCidr, String nicId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("GCE currently only supports routing to instances.");
	}

	@Override
	public Route addRouteToVirtualMachine(String toRoutingTableId, IPVersion version, String destinationCidr, String vmId) throws CloudException, InternalException {
		//Using toRoutingTableId as vlanId - GCE supports vlan specific routes
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            logger.error("No context was set for this request");
            throw new InternalException("No context was set for this request");
        }
        Operation job = null;
        try{
            Compute gce = provider.getGoogleCompute();
            VirtualMachine vm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(vmId);

            com.google.api.services.compute.model.Route route = new com.google.api.services.compute.model.Route();
            route.setName((destinationCidr + "-" + vmId).toLowerCase());
            route.setDestRange(destinationCidr);
            route.setNextHopInstance((String)vm.getTag("contentLink"));
            route.setNextHopIp(vm.getPrivateAddresses()[0].getIpAddress());
            route.setNextHopGateway("/projects/<project-id>/global/gateways/default-internet-gateway");//Currently only supports Internet Gateway

            job = gce.routes().insert(ctx.getAccountNumber(), route).execute();

            GoogleMethod method = new GoogleMethod(provider);
            String routeName = method.getOperationTarget(ctx, job, GoogleOperationType.GLOBAL_OPERATION, "", "", false);
            com.google.api.services.compute.model.Route googleRoute = gce.routes().get(ctx.getAccountNumber(), routeName).execute();

            Route r = toRoute(googleRoute);
            return r;
	    } catch (IOException ex) {
            logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException("An error occurred while creating the route: " + ex.getMessage());
		}
	}

	@Override
    public @Nonnull VLAN createVlan(@Nonnull String cidr, @Nonnull String name, @Nonnull String description, @Nonnull String domainName, @Nonnull String[] dnsServers, @Nonnull String[] ntpServers) throws CloudException, InternalException {
		if(!getCapabilities().allowsNewVlanCreation()) {
			throw new OperationNotSupportedException();
		}
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			logger.error("No context was set for this request");
			throw new InternalException("No context was set for this request");
		}

		String regionId = ctx.getRegionId();
		if( regionId == null ) {
			logger.error("No region was set for this request");
			throw new CloudException("No region was set for this request");
		}

        Operation job = null;
        try{
            Compute gce = provider.getGoogleCompute();
            Network network = new Network();
            network.setName(name.toLowerCase());
            network.setDescription(description);
            network.setIPv4Range(cidr);
            job = gce.networks().insert(ctx.getAccountNumber(), network).execute();

            GoogleMethod method = new GoogleMethod(provider);
            String vLanName = method.getOperationTarget(ctx, job, GoogleOperationType.GLOBAL_OPERATION, "", "", false);

            Network googleVLan = gce.networks().get(ctx.getAccountNumber(), vLanName).execute();
            VLAN vLan = toVlan(googleVLan, ctx);
            return vLan;
	    } catch (IOException ex) {
			logger.error("An error occurred while creating vlan: " + ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException("An error occurred while creating vlan: " + ex.getMessage());
		}
	}

    private transient volatile GCENetworkCapabilities capabilities;
    @Override
    public @Nonnull GCENetworkCapabilities getCapabilities(){
        if(capabilities == null){
            capabilities = new GCENetworkCapabilities(provider);
        }
        return capabilities;
    }

	@Override
    @Deprecated
	public @Nonnull String getProviderTermForNetworkInterface(@Nonnull Locale locale) {
		return "network interface";
	}

	@Override
    @Deprecated
	public @Nonnull String getProviderTermForVlan(@Nonnull Locale locale) {
		return "network";
	}

    @Override
    @Deprecated
    public @Nonnull String getProviderTermForSubnet(@Nonnull Locale locale) {
        return "";
    }

	@Override
	public NetworkInterface getNetworkInterface(@Nonnull String nicId)throws CloudException, InternalException {
		Iterable<NetworkInterface> nicList = listNetworkInterfaces();
		for (NetworkInterface nic: nicList) {
			if (nic.getName().equals(nicId)) return nic;
		}
		return null;
	}

	@Override
	public RoutingTable getRoutingTableForVlan(@Nonnull String vlanId)throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");
	}

	@Override
	public @Nullable VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new CloudException("No context was set for this request");
		}

        try{
            Compute gce = provider.getGoogleCompute();
            Network network = gce.networks().get(ctx.getAccountNumber(), vlanId).execute();
            return toVlan(network, ctx);
	    } catch (IOException ex) {
	    	if ((ex.getMessage() != null) && (ex.getMessage().contains("404 Not Found")))  // vlan not found, its ok, return null.
	    	    return null;
	    	logger.error("An error occurred while getting network " + vlanId + ": " + ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
	            GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException(ex.getMessage());
		}
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public @Nonnull Collection<String> listFirewallIdsForNIC(@Nonnull String nicId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
	}

    @Override
    public @Nonnull Collection<InternetGateway> listInternetGateways(@Nullable String vlanId) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
    }

    @Override
    public InternetGateway getInternetGatewayById(@Nonnull String gatewayId) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
    }

    @Override
    public @Nullable String getAttachedInternetGatewayId(@Nonnull String vlanId) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
    }

    @Override
    public void removeInternetGatewayById(@Nonnull String id) throws CloudException, InternalException{
        throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
    }

	@Override
	public @Nonnull Iterable<ResourceStatus> listNetworkInterfaceStatus() throws CloudException, InternalException {
		Iterable<NetworkInterface> nicList = listNetworkInterfaces();
		List<ResourceStatus> status = new ArrayList<ResourceStatus>();
		for (NetworkInterface nic: nicList) {
			status.add(new ResourceStatus(nic.getName(), nic.getCurrentState()));
		}
		return status;
	}

	@Override
	public @Nonnull Iterable<NetworkInterface> listNetworkInterfaces() throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not currently implemented for " + provider.getCloudName());
	}

	@Override
	public @Nonnull Iterable<NetworkInterface> listNetworkInterfacesForVM(@Nonnull String forVmId) throws CloudException, InternalException {
		Iterable<NetworkInterface> nics = listNetworkInterfaces();
		List<NetworkInterface> vmNics = new ArrayList<NetworkInterface>();
		for (NetworkInterface nic: nics) {
			if (nic.getProviderVirtualMachineId().equals(forVmId)) {
				vmNics.add(nic);
			}
		}
		return vmNics;
	}

	@Override
	public @Nonnull Iterable<NetworkInterface> listNetworkInterfacesInSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets are not supported by GCE.");
	}

	@Override
	public @Nonnull Iterable<NetworkInterface> listNetworkInterfacesInVLAN(@Nonnull String vlanId) throws CloudException, InternalException {
		Iterable<NetworkInterface> nics = listNetworkInterfaces();
		List<NetworkInterface> vlanNics = new ArrayList<NetworkInterface>();
		for (NetworkInterface nic: nics) {
			if (nic.getProviderVlanId().equals(vlanId)) {
				vlanNics.add(nic);
			}
		}
		return vlanNics;
	}

	@Override
	public @Nonnull Iterable<Networkable> listResources(@Nonnull String inVlanId)throws CloudException, InternalException {
		Collection<Networkable> resources = new ArrayList<Networkable>();
		Iterable<NetworkInterface> nics = listNetworkInterfacesInVLAN(inVlanId);
		for (NetworkInterface nic: nics) {
			IpAddress ip = new IpAddress();
			ip.setVersion(IPVersion.IPV4);
			ip.setProviderNetworkInterfaceId(nic.getProviderNetworkInterfaceId());
			ip.setRegionId(nic.getProviderRegionId());
			ip.setServerId(nic.getProviderVirtualMachineId());
			ip.setProviderVlanId(nic.getProviderVlanId());
			resources.add(ip);
		}

		return resources;

	}

	@Override
	public @Nonnull Iterable<RoutingTable> listRoutingTables(@Nonnull String inVlanId)throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables and subnets not supported.");
	}

	@Override
	public @Nonnull Iterable<Subnet> listSubnets(@Nonnull String inVlanId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets not supported.");
	}

	@Override
	public @Nonnull Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
        APITrace.begin(provider, "VLAN.listVlanStatus");
        try{
            Compute gce = provider.getGoogleCompute();
            ArrayList<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
            try{
                NetworkList networks = gce.networks().list(provider.getContext().getAccountNumber()).execute();
                for(Network network : networks.getItems()){
                    statuses.add(new ResourceStatus(network.getName(), VLANState.AVAILABLE));
                }
                return statuses;
    	    } catch (IOException ex) {
                logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred getting VLAN statuses");
    		}
        }
        finally {
            APITrace.end();
        }
	}

	@Override
	public @Nonnull Iterable<VLAN> listVlans() throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new InternalException("No context was established");
		}

        ArrayList<VLAN> vlans = new ArrayList<VLAN>();
        try{
            Compute gce = provider.getGoogleCompute();
            List<Network> networks = gce.networks().list(ctx.getAccountNumber()).execute().getItems();
            for(int i=0;i<networks.size();i++){
                VLAN vlan = toVlan(networks.get(i), ctx);
                if(vlan != null)vlans.add(vlan);
            }
	    } catch (IOException ex) {
            logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
	            throw new CloudException("An error occurred while listing VLans: " + ex.getMessage());
		}
        return vlans;
	}

	@Override
	public void removeVlan(String vlanId) throws CloudException, InternalException {
        APITrace.begin(provider, "VLAN.removeVlan");
        try{
            Operation job = null;
            try{
                Compute gce = provider.getGoogleCompute();
                VLAN vlan = getVlan(vlanId);
                job = gce.networks().delete(provider.getContext().getAccountNumber(), vlan.getName()).execute();
                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "")){
                    throw new CloudException("An error occurred while removing network: " + vlanId + ": Operation timed out");
                }
    	    } catch (IOException ex) {
	            logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred while removing network: " + vlanId + ": " + ex.getMessage());
    		}
        }
        finally{
            APITrace.end();
        }
	}

    private @Nullable VLAN toVlan(Network network, ProviderContext ctx){
        VLAN vLan = new VLAN();
        //vLan.setProviderVlanId(network.getId() + ""); - GCE uses name as IDs
        vLan.setProviderVlanId(network.getName());
        vLan.setName(network.getName());
        vLan.setDescription((network.getDescription() == null || network.getDescription().equals("")) ? network.getName() : network.getDescription());
        //VLANs in GCE don't have regions - using new VisibleScope variable instead
        //vLan.setProviderRegionId(ctx.getRegionId());
        vLan.setVisibleScope(VisibleScope.ACCOUNT_GLOBAL);
        vLan.setCidr(network.getIPv4Range());
        vLan.setCurrentState(VLANState.AVAILABLE);
        vLan.setProviderOwnerId(provider.getContext().getAccountNumber());
        vLan.setSupportedTraffic(IPVersion.IPV4);
        vLan.setTag("contentLink", network.getSelfLink());

        return vLan;
    }

    private @Nullable Route toRoute(com.google.api.services.compute.model.Route googleRoute){
        //TODO: This needs some work
        return Route.getRouteToVirtualMachine(IPVersion.IPV4, googleRoute.getDestRange(), provider.getContext().getAccountNumber(), googleRoute.getNextHopInstance());
    }

	@Override
	public void removeInternetGatewayTags(String internetGatewayId, Tag... tags)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeRoutingTableTags(String routingTableId, Tag... tags)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateRoutingTableTags(String routingTableId, Tag... tags)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateInternetGatewayTags(String internetGatewayId, Tag... tags)
			throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}
}