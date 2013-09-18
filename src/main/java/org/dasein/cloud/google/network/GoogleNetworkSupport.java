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

import java.util.ArrayList;
import java.util.Collection;
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
import org.dasein.cloud.Tag;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.*;
import org.dasein.util.CalendarWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Implements the network services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleNetworkSupport extends AbstractVLANSupport {

	static private final Logger logger = Google.getLogger(GoogleNetworkSupport.class);
	private Google provider;

	GoogleNetworkSupport( Google cloud ) {
    super( cloud );
    this.provider = cloud;
  }

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public Route addRouteToAddress(String toRoutingTableId, IPVersion version,
			String destinationCidr, String address) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public Route addRouteToGateway(String toRoutingTableId, IPVersion version,
			String destinationCidr, String gatewayId) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public Route addRouteToNetworkInterface(String toRoutingTableId,
			IPVersion version, String destinationCidr, String nicId)
					throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public Route addRouteToVirtualMachine(String toRoutingTableId,
			IPVersion version, String destinationCidr, String vmId)
					throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public boolean allowsMultipleTrafficTypesOverSubnet()
			throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean allowsMultipleTrafficTypesOverVlan() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean allowsNewNetworkInterfaceCreation() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean allowsNewVlanCreation() throws CloudException,
	InternalException {
		return true;
	}

	@Override
	public boolean allowsNewSubnetCreation() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public void assignRoutingTableToSubnet(String subnetId,
			String routingTableId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public void assignRoutingTableToVlan(String vlanId, String routingTableId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public void attachNetworkInterface(String nicId, String vmId, int index)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Attaching a network interface is not supported.");

	}

	@Override
	public String createInternetGateway(String forVlanId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Creating internet gateways not supported.");
	}

  @Override
  @Nullable
  public String getAttachedInternetGatewayId( @Nonnull String vlanId ) throws CloudException, InternalException {
    throw new OperationNotSupportedException( "Internet gateways not supported." );
  }

  @Override
  @Nullable
  public InternetGateway getInternetGatewayById( @Nonnull String gatewayId ) throws CloudException, InternalException {
    throw new OperationNotSupportedException( "Internet gateways not supported." );
  }

  @Override
  @Nullable
  public Collection<InternetGateway> listInternetGateways( @Nullable String vlanId ) throws CloudException, InternalException {
    throw new OperationNotSupportedException( "Internet gateways not supported." );
  }

  @Override
  @Nullable
  public void removeInternetGatewayById( @Nonnull String id ) throws CloudException, InternalException {
    throw new OperationNotSupportedException( "Internet gateways not supported." );
  }

  @Override
	public String createRoutingTable(String forVlanId, String name,
			String description) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");
	}

	@Override
	public NetworkInterface createNetworkInterface(NICCreateOptions options)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Creating network interfaces is not supported.");
	}

	@Override
	public Subnet createSubnet(String cidr, String inProviderVlanId,
			String name, String description) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Subnets not supported.");
	}

	@Override
	public Subnet createSubnet(SubnetCreateOptions options)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets not supported.");
	}

	@Override
	public VLAN createVlan(String cidr, String name, String description,
			String domainName, String[] dnsServers, String[] ntpServers)
					throws CloudException, InternalException {

		if( !allowsNewVlanCreation() ) {
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

		GoogleMethod method = new GoogleMethod(provider);

		JSONObject payload = new JSONObject();
		name = name.replace(" ", "").replace("-", "").replace(":", "");

		try {
			payload.put("name", name);
			payload.put("IPv4Range", cidr);
			payload.put("description", description);
		} catch (JSONException e) {
			e.printStackTrace();
			logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
			throw new CloudException(e);
		}

		JSONObject response = method.post(GoogleMethod.NETWORK, payload);

		String vlanName = null;

		String status = method.getOperationStatus(GoogleMethod.GLOBAL_OPERATION, response);
		if (status != null && status.equals("DONE")) {
			if( response.has("targetLink") ) {
				try {
					vlanName = response.getString("targetLink");
				} catch (JSONException e) {
					e.printStackTrace();
					logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
					throw new CloudException(e);
				}

				vlanName = GoogleMethod.getResourceName(vlanName, GoogleMethod.NETWORK);
				return getVlan(vlanName);
			}
		}
		throw new CloudException("No networks was created.");

	}

	@Override
	public void detachNetworkInterface(String nicId) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("Detaching a network interface is not supported.");

	}

	@Override
	public int getMaxNetworkInterfaceCount() throws CloudException,
	InternalException {
		return -2;
	}

	@Override
	public int getMaxVlanCount() throws CloudException, InternalException {
		return -2;
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
	public NetworkInterface getNetworkInterface(String nicId)
			throws CloudException, InternalException {


		Iterable<NetworkInterface> nicList = listNetworkInterfaces();
		for (NetworkInterface nic: nicList) {
			if (nic.getName().equals(nicId)) return nic;
		}
		return null;
	}

	@Override
	public RoutingTable getRoutingTableForSubnet(String subnetId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables and subnets not supported.");
	}

	@Override
	public Requirement getRoutingTableSupport() throws CloudException,
	InternalException {
		return Requirement.NONE;
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

	@Override
	public Requirement getSubnetSupport() throws CloudException,
	InternalException {
		return Requirement.NONE;
	}

	@Override
	public VLAN getVlan(String vlanId) throws CloudException, InternalException {

		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new CloudException("No context was set for this request");
		}

		GoogleMethod method = new GoogleMethod(provider);
		JSONArray array = method.get(GoogleMethod.NETWORK);
		if (array != null)
			for (int i = 0; i < array.length(); i++) {
				try {
					JSONObject network = (JSONObject) array.getJSONObject(i);
					if (network.has("name")) {
						String name = network.getString("name");
						if (name.equals((String) vlanId)) {
							VLAN vlan = toNetwork(network, ctx);
							return vlan;
						}
					}

				} catch (JSONException e ) {
					logger.error("Failed to parse JSON: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}
			}
		return null;
	}

	public @Nullable VLAN toNetwork(@Nullable JSONObject json, @Nonnull ProviderContext ctx) throws CloudException {

		if( json == null ) {
			return null;
		}

		VLAN network = new VLAN();
		network.setProviderOwnerId(ctx.getAccountNumber());
		network.setProviderRegionId(ctx.getRegionId());
		network.setProviderDataCenterId(ctx.getRegionId() + "-a");
		network.setCurrentState(VLANState.AVAILABLE);

		network.setDomainName("dasein.org");

		String[] dnsServers = new String[] { "192.168.1.1" };
		String[] ntpServers = new String[] { "192.168.1.1" };

		network.setDnsServers(dnsServers);
		network.setNtpServers(ntpServers);

		try {

			if (json.has("name")) {
				String name = (String) json.get("name");
				network.setProviderVlanId(name);
				network.setName(name);
			}

			if (json.has("IPv4Range")) {
				network.setCidr((String)json.get("IPv4Range"));

			}
			if (json.has("gatewayIPv4")) {
				// TODO: Not sure how to map the gateway. There is no equivalent element in network (Vinothini)
			}
			
		} catch (JSONException e ) {
			logger.error("Failed to parse JSON from the cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}

		if( network.getProviderVlanId() == null ) {
			return null;
		}

		if( network.getName() == null ) {
			network.setName(network.getProviderVlanId());
		}
		if( network.getDescription() == null ) {
			network.setDescription(network.getName());
		}

		return network;
	}


	@Override
	public Requirement identifySubnetDCRequirement() {
		return Requirement.NONE;
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
		for (NetworkInterface nic: nics) {
			if (nic.getName().equals(nicId)) {
				String network = nic.getProviderVlanId();
				Iterable<Firewall> firewalls = provider.getNetworkServices().getFirewallSupport().list();
				for(Firewall firewall: firewalls) {
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
		for (NetworkInterface nic: nicList) {
			status.add(new ResourceStatus(nic.getName(), nic.getCurrentState()));
		}
		return status;
	}

	@Override
	public Iterable<NetworkInterface> listNetworkInterfaces()
			throws CloudException, InternalException {
		GoogleMethod method = new GoogleMethod(provider);
		ProviderContext ctx = provider.getContext();
		JSONArray list = method.get(GoogleMethod.SERVER);
		List<NetworkInterface> nicList = new ArrayList<NetworkInterface>();

		if( list != null ) 
			for( int i=0; i<list.length(); i++ ) {
				try {

					JSONObject json = list.getJSONObject(i);
					if (json.has("networkInterfaces")) {
						JSONArray networkInterfaces = json.getJSONArray("networkInterfaces");
						for (int j = 0; j < networkInterfaces.length(); j++) {
							JSONObject networkInterface = networkInterfaces.getJSONObject(j);
							if (networkInterface.has("name")) {
								String name = networkInterface.getString("name");
								NetworkInterface nic = new NetworkInterface();
								nic.setName(name);
								nic.setProviderNetworkInterfaceId(name);
								nic.setProviderDataCenterId(ctx.getRegionId() + "-a");
								nic.setProviderRegionId(ctx.getRegionId());

								nic.setProviderOwnerId(ctx.getAccountNumber());
								nic.setCurrentState(NICState.IN_USE);

								if (json.has("name"))nic.setProviderVirtualMachineId(json.getString("name"));
								if (networkInterface.has("network")) {
									String providerVlanId = networkInterface.getString("network");
									providerVlanId = GoogleMethod.getResourceName(providerVlanId, GoogleMethod.NETWORK);
									nic.setProviderVlanId(providerVlanId);
								}
								List<RawAddress> addresses = new ArrayList<RawAddress>();
								if (networkInterface.has("networkIP")) {
									String ip = networkInterface.getString("networkIP");
									addresses.add(new RawAddress(ip, IPVersion.IPV4));
								}

								if (networkInterface.has("accessConfigs")) {
									JSONArray accessConfigs = networkInterface.getJSONArray("accessConfigs");
									for (int k=0; k < accessConfigs.length(); k++) {
										JSONObject access = accessConfigs.getJSONObject(k);
										if (access.has("natIP")) {
											String ip = access.getString("natIP");
											addresses.add(new RawAddress(ip, IPVersion.IPV4));
										}
									}
								}
								nic.setIpAddresses((RawAddress[]) addresses.toArray());
								nicList.add(nic);
							}
						}
					}

				} catch( JSONException e ) {
					logger.error("Failed to parse JSON: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}
			}
		return nicList;
	}

	@Override
	public Iterable<NetworkInterface> listNetworkInterfacesForVM(String forVmId)
			throws CloudException, InternalException {
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
	public Iterable<NetworkInterface> listNetworkInterfacesInSubnet(
			String subnetId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Subnets not supported.");
	}

	@Override
	public Iterable<NetworkInterface> listNetworkInterfacesInVLAN(String vlanId)
			throws CloudException, InternalException {
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
	public Iterable<Networkable> listResources(String inVlanId)
			throws CloudException, InternalException {
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
	public Iterable<RoutingTable> listRoutingTables(String inVlanId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables and subnets not supported.");
	}

	@Override
	public Iterable<Subnet> listSubnets(String inVlanId) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("Subnets not supported.");
	}

	@Override
	public Iterable<IPVersion> listSupportedIPVersions() throws CloudException,
	InternalException {
		Collection<IPVersion> versions = new ArrayList<IPVersion>();
		versions.add(IPVersion.IPV4);
		return versions;

	}

	@Override
	public Iterable<ResourceStatus> listVlanStatus() throws CloudException,
	InternalException {
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new InternalException("No context was established");
		}

		GoogleMethod method = new GoogleMethod(provider);
		JSONArray array = method.get(GoogleMethod.NETWORK);

		ArrayList<ResourceStatus> status = new ArrayList<ResourceStatus>();

		if (array != null)
			for( int i=0; i<array.length(); i++ ) {

				JSONObject network = null;
				try {
					network = array.getJSONObject(i);
				} catch (JSONException e ) {
					logger.error("Failed to parse JSON: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}

				if( network.has("name")) {
					ResourceStatus vlanStatus = toVLANStatus(network);
					if( vlanStatus != null ) {
						status.add(vlanStatus);
					}
				}
			}
		return status;
	}

	public @Nullable ResourceStatus toVLANStatus(@Nullable JSONObject network) throws CloudException {

		String networkId = null;
		if (network.has("name"))
			try {
				networkId = (String) network.getString("name");
			} catch (JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		return new ResourceStatus(networkId, VLANState.AVAILABLE);
	}

	@Override
	public Iterable<VLAN> listVlans() throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new InternalException("No context was established");
		}

		GoogleMethod method = new GoogleMethod(provider);
		JSONArray array = method.get(GoogleMethod.NETWORK);

		ArrayList<VLAN> networks = new ArrayList<VLAN>();

		if (array != null)
			for( int i=0; i< array.length(); i++ ) {

				JSONObject network = null;
				try {
					network = array.getJSONObject(i);
				} catch (JSONException e ) {
					logger.error("Failed to parse JSON: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}
				VLAN vlan = toNetwork(network, ctx);
				if( vlan != null ) networks.add(vlan);
			}
		return networks;

	}

	@Override
	public void removeInternetGateway(String forVlanId) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("Removing internet gateways not supported.");

	}

	@Override
	public void removeNetworkInterface(String nicId) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("De-provisioning network interfaces is not supported.");

	}

	@Override
	public void removeRoute(String inRoutingTableId, String destinationCidr)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public void removeRoutingTable(String routingTableId)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Routing tables not supported.");

	}

	@Override
	public void removeSubnet(String providerSubnetId) throws CloudException,
	InternalException {
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

	@Override
	public void removeVlan(String vlanId) throws CloudException,
	InternalException {
		GoogleMethod method = new GoogleMethod(provider);

		method.delete(GoogleMethod.NETWORK, new GoogleMethod.Param("id", vlanId));
		long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 15L);

		while( timeout > System.currentTimeMillis() ) {
			VLAN vlan = getVlan(vlanId);

			if( vlan == null) {
				return;
			}
			try { Thread.sleep(15000L); }
			catch( InterruptedException ignore ) { }
		}
		throw new CloudException("VLAN deletion failed.");

	}

	@Override
	public boolean supportsInternetGatewayCreation() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean supportsRawAddressRouting() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public void updateVLANTags(String arg0, Tag... arg1) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("Google does not support updating vlan tags");

	}

	@Override
	public void updateVLANTags(String[] arg0, Tag... arg1)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support updating vlan tags");

	}

}
