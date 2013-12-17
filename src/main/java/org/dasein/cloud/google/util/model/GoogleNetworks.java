package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.Network;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANState;

/**
 * @author Eduard Bakaev
 * @since 16.12.2013
 */
public class GoogleNetworks {

	/**
	 * Default google network name
	 */
	public static final String DEFAULT = "default";

	/**
	 * Creates {@link VLAN} Object based on {@link Network} Googel object
	 *
	 * @param googleNetworkDetails google object
	 * @param ctx                  context
	 * @return VLAN object
	 * @throws org.dasein.cloud.CloudException
	 *
	 */
	public static VLAN toNetwork(Network googleNetworkDetails, ProviderContext ctx) throws CloudException {
		if (googleNetworkDetails == null) {
			return null;
		}
		VLAN network = new VLAN();
		network.setProviderOwnerId(ctx.getAccountNumber());
		network.setProviderRegionId(ctx.getRegionId());
		network.setProviderDataCenterId(ctx.getRegionId() + "-a");
		network.setCurrentState(VLANState.AVAILABLE);

		network.setDomainName("dasein.org");

		String[] dnsServers = new String[]{"192.168.1.1"};
		String[] ntpServers = new String[]{"192.168.1.1"};
		network.setDnsServers(dnsServers);
		network.setNtpServers(ntpServers);

		network.setProviderVlanId(googleNetworkDetails.getName());
		network.setName(googleNetworkDetails.getName());
		network.setCidr(googleNetworkDetails.getIPv4Range());

		if (network.getProviderVlanId() == null) {
			return null;
		}
		if (network.getName() == null) {
			network.setName(network.getProviderVlanId());
		}
		if (network.getDescription() == null) {
			network.setDescription(network.getName());
		}
		return network;
	}

}
