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

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.NetworkList;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.FluentIterable;
import org.dasein.cloud.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.compute.server.OperationSupport;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.GoogleLogger;
import org.dasein.cloud.google.util.model.GoogleNetworks;
import org.dasein.cloud.google.util.model.GoogleOperations;
import org.dasein.cloud.network.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Implements the network services supported in the Google API.
 *
 * @author ebakaev
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleNetworkSupport extends AbstractVLANSupport {

	static private final Logger logger = GoogleLogger.getLogger(GoogleNetworkSupport.class);
	private Google provider;
	private OperationSupport<Operation> operationSupport;

	GoogleNetworkSupport(Google provider) {
		super(provider);
		this.provider = provider;
		this.operationSupport = provider.getComputeServices().getOperationsSupport();
	}

	@Override
	public
	@Nullable
	String getAttachedInternetGatewayId(@Nonnull String vlanId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Internet gateways not supported.");
	}

	@Override
	public
	@Nullable
	InternetGateway getInternetGatewayById(@Nonnull String gatewayId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Internet gateways not supported.");
	}

	@Override
	public
	@Nullable
	Collection<InternetGateway> listInternetGateways(@Nullable String vlanId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Internet gateways not supported.");
	}

	@Override
	public void removeInternetGatewayById(@Nonnull String id) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Internet gateways not supported.");
	}

	@Nonnull
	@Override
	public VLAN createVlan(@Nonnull VlanCreateOptions vco) throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		if (provider.getContext().getRegionId() == null) {
			logger.error("No region was set for this request");
			throw new CloudException("No region was set for this request");
		}

		Compute compute = provider.getGoogleCompute();
		Operation operation = null;
		try {
			Network newNetwork = new Network();
			newNetwork.setName(vco.getName());
			newNetwork.setIPv4Range(vco.getCidr());
			newNetwork.setDescription(vco.getDescription());
			Compute.Networks.Insert createAction = compute.networks().insert(provider.getContext().getAccountNumber(), newNetwork);
			operation = createAction.execute();
			//waiting until network is created.
			operation = operationSupport.waitUntilOperationCompletes(operation);
			if (operation != null && operation.getStatus() != null
					&& GoogleOperations.OperationStatus.DONE.equals(GoogleOperations.OperationStatus.fromOperation(operation))) {
				return getVlan(vco.getName());
			}
		} catch (IOException e) {
			logger.error("Failed to create the new Google Network object '" + vco.getName() + "' : " + e.getMessage());
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		throw new CloudException("The new Google Network '" + vco.getName() + "' wasn't created");
	}

	@Override
	public VLAN createVlan(String cidr, String name, String description,
	                       String domainName, String[] dnsServers, String[] ntpServers)
			throws CloudException, InternalException {
		return createVlan(VlanCreateOptions.getInstance(name, description, cidr, domainName, dnsServers, ntpServers));
	}

	@Override
	public String getProviderTermForNetworkInterface(Locale locale) {
		return "network interface";
	}

	@Override
	public String getProviderTermForSubnet(Locale locale) {
		return "";
	}

	@Override
	public String getProviderTermForVlan(Locale locale) {
		return "network";
	}


	@Override
	public RoutingTable getRoutingTableForSubnet(String subnetId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables and subnets not supported.");
	}

	@Override
	public RoutingTable getRoutingTableForVlan(String vlanId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");
	}

	@Override
	public Subnet getSubnet(String subnetId) throws CloudException,
			InternalException {
		return null;
	}

	/**
	 * Gets VLAN by its id.
	 * Google API is used.
	 *
	 * @param vlanId to search
	 * @return VLAN
	 * @throws CloudException
	 * @throws InternalException
	 */
	@Override
	public VLAN getVlan(String vlanId) throws CloudException, InternalException {
		Preconditions.checkNotNull(vlanId, "VLAN id has to be specified");
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		ProviderContext ctx = provider.getContext();

		Compute compute = provider.getGoogleCompute();
		try {
			Compute.Networks.Get getNetworkAction = compute.networks().get(provider.getContext().getAccountNumber(), vlanId);
			Network network = getNetworkAction.execute();
			if (network != null) {
				return GoogleNetworks.toNetwork(network, ctx);
			}
		} catch (IOException e) {
			logger.error("Failed to get Google Network : " + e.getMessage());
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		return null;
	}

	@Override
	public boolean isNetworkInterfaceSupportEnabled() throws CloudException,
			InternalException {
		return true;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isSubnetDataCenterConstrained() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public boolean isVlanDataCenterConstrained() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public Collection<String> listFirewallIdsForNIC(String nicId)
			throws CloudException, InternalException {
		Iterable<NetworkInterface> nics = listNetworkInterfaces();
		List<String> firewallList = new ArrayList<String>();
		for (NetworkInterface nic : nics) {
			if (nic.getName().equals(nicId)) {
				String network = nic.getProviderVlanId();
				Iterable<Firewall> firewalls = provider.getNetworkServices().getFirewallSupport().list();
				for (Firewall firewall : firewalls) {
					if (firewall.getProviderVlanId().equals(network)) {
						firewallList.add(firewall.getProviderFirewallId());
					}
				}
			}
		}
		return firewallList;
	}

	@Override
	public Iterable<ResourceStatus> listNetworkInterfaceStatus()
			throws CloudException, InternalException {
		Iterable<NetworkInterface> nicList = listNetworkInterfaces();
		List<ResourceStatus> status = new ArrayList<ResourceStatus>();
		for (NetworkInterface nic : nicList) {
			status.add(new ResourceStatus(nic.getName(), nic.getCurrentState()));
		}
		return status;
	}

	@Override
	public Iterable<NetworkInterface> listNetworkInterfaces() throws CloudException, InternalException {
		throw new OperationNotSupportedException("Is not implemented yet");
	}

	@Override
	public Iterable<NetworkInterface> listNetworkInterfacesForVM(String forVmId)
			throws CloudException, InternalException {
		Iterable<NetworkInterface> nics = listNetworkInterfaces();
		List<NetworkInterface> vmNics = new ArrayList<NetworkInterface>();
		for (NetworkInterface nic : nics) {
			if (nic.getProviderVirtualMachineId().equals(forVmId)) {
				vmNics.add(nic);
			}
		}
		return vmNics;
	}

	@Override
	public Iterable<NetworkInterface> listNetworkInterfacesInSubnet(
			String subnetId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets not supported.");
	}

	@Override
	public Iterable<NetworkInterface> listNetworkInterfacesInVLAN(String vlanId) throws CloudException, InternalException {
		Iterable<NetworkInterface> nics = listNetworkInterfaces();
		List<NetworkInterface> vlanNics = new ArrayList<NetworkInterface>();
		for (NetworkInterface nic : nics) {
			if (nic.getProviderVlanId().equals(vlanId)) {
				vlanNics.add(nic);
			}
		}
		return vlanNics;
	}

	@Override
	public Iterable<Networkable> listResources(String inVlanId)
			throws CloudException, InternalException {
		Collection<Networkable> resources = new ArrayList<Networkable>();
		Iterable<NetworkInterface> nics = listNetworkInterfacesInVLAN(inVlanId);
		for (NetworkInterface nic : nics) {
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
	public Iterable<RoutingTable> listRoutingTables(String inVlanId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables and subnets not supported.");
	}

	@Override
	public Iterable<Subnet> listSubnets(String inVlanId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets not supported.");
	}

	@Override
	public Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		List<VLAN> networks = FluentIterable.from(listVlans()).toList();
		ArrayList<ResourceStatus> status = new ArrayList<ResourceStatus>();
		if (networks != null) {
			for (VLAN vlan : networks) {
				status.add(new ResourceStatus(vlan.getName(), VLANState.AVAILABLE));
			}
		}
		return status;
	}


	/**
	 * Returns list of available google networks for provided context.
	 *
	 * @return list of networks (VLANs)
	 * @throws CloudException
	 */
	@Override
	public Iterable<VLAN> listVlans() throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}
		ArrayList<VLAN> networks = new ArrayList<VLAN>();
		Compute compute = provider.getGoogleCompute();
		try {
			Compute.Networks.List networkList = compute.networks().list(provider.getContext().getAccountNumber());
			NetworkList list = networkList.execute();
			if (list != null && list.size() > 0) {
				for (Network network : list.getItems()) {
					VLAN vlan = GoogleNetworks.toNetwork(network, ctx);
					if (vlan != null) {
						networks.add(vlan);
					}
				}
			}
		} catch (IOException e) {
			logger.error("Failed to get list of Networks : " + e.getMessage());
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		return networks;
	}

	@Override
	public void removeInternetGateway(String forVlanId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Removing internet gateways not supported.");
	}

	@Override
	public void removeNetworkInterface(String nicId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("De-provisioning network interfaces is not supported.");
	}

	@Override
	public void removeRoute(String inRoutingTableId, String destinationCidr) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public void removeRoutingTable(String routingTableId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");
	}

	@Override
	public void removeSubnet(String providerSubnetId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets not supported");
	}

	@Override
	public void removeVLANTags(String arg0, Tag... arg1) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Google does not support removing vlan tags");

	}

	@Override
	public void removeVLANTags(String[] arg0, Tag... arg1)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support removing vlan tags");

	}

	/**
	 * Removes Google network by its Id
	 *
	 * @param vlanId to remove
	 * @throws CloudException
	 * @throws InternalException
	 */
	@Override
	public void removeVlan(String vlanId) throws CloudException, InternalException {
		Preconditions.checkNotNull(vlanId, "VLAN is has to be specified");
		Compute compute = provider.getGoogleCompute();
		Operation operation = null;
		try {
			Compute.Networks.Delete deleteAction = compute.networks().delete(provider.getContext().getAccountNumber(), vlanId);
			operation = deleteAction.execute();
		} catch (IOException e) {
			logger.error("Failed to delete Google Network object '" + vlanId + "' : " + e.getMessage());
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}
	}

	@Override
	public boolean supportsInternetGatewayCreation() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean supportsRawAddressRouting() throws CloudException, InternalException {
		return false;
	}

	@Override
	public void updateVLANTags(String arg0, Tag... arg1) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support updating vlan tags");
	}

	@Override
	public void updateVLANTags(String[] arg0, Tag... arg1) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support updating vlan tags");
	}

	@Override
	public void removeInternetGatewayTags(@Nonnull String internetGatewayId, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Internet gateways not supported.");
	}

	@Override
	public void removeRoutingTableTags(@Nonnull String routingTableId, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables are not supported");
	}

	@Override
	public void removeSubnetTags(@Nonnull String providerSubnetId, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets are not supported");
	}

	@Override
	public void updateRoutingTableTags(@Nonnull String routingTableId, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables are not supported");
	}

	@Override
	public void updateSubnetTags(@Nonnull String providerSubnetId, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets are not supported");
	}

	@Override
	public void updateInternetGatewayTags(@Nonnull String internetGatewayId, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Internet gateways not supported.");
	}

}
