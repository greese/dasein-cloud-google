package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.Address;
import com.google.api.services.compute.model.Network;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.network.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
	 * Creates {@link VLAN} Object based on {@link Network} Google object
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


    public static List<IpAddress> toAddressList(@Nullable Collection<Address> items) {
        if (items == null) {
            return Collections.emptyList();
        }

        ArrayList<IpAddress> addresses = new ArrayList<IpAddress>(items.size());
        for (Address item : items) {
            addresses.add(toIpAddress(item));
        }
        return addresses;
    }

    @Nullable
    public static IpAddress toIpAddress(@Nullable Address address) {
        if (address == null) {
            return null;
        }

        IpAddress ipAddress = new IpAddress();

        ipAddress.setIpAddressId(address.getName());
        ipAddress.setAddress(address.getAddress());
        ipAddress.setRegionId(address.getRegion());
        ipAddress.setAddressType(AddressType.PUBLIC);
        ipAddress.setVersion(IPVersion.IPV4);
        ipAddress.setForVlan(false);
        if (address.getUsers() != null && address.getUsers().size() > 0) {
            for (String user : address.getUsers()) {
                user = user.substring(user.lastIndexOf("/") + 1);
                ipAddress.setServerId(user);
            }
        }

        return ipAddress;
    }

    public static Iterable<ResourceStatus> toResourceStatusIterable(List<Address> items) {
        ArrayList<ResourceStatus> addresses = new ArrayList<ResourceStatus>(items.size());
        for (Address item : items) {
            addresses.add(toStatus(item));
        }
        return addresses;
    }

    public static ResourceStatus toStatus(Address address){
        return new ResourceStatus(address.getName(), address.getStatus().equals("RESERVED") ? false : true);
    }
}
