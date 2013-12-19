package org.dasein.cloud.google.util.model;

import com.google.api.client.util.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.network.FirewallCreateOptions;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Eduard Bakaev
 * @since 13.12.2013
 */
public final class GoogleFirewalls {

	/**
	 * Default prefix for instance name
	 */
	public static final String DEFAULT_FIREWALL_NAME_PREFIX = "sg-g-";

	public static final String PROVIDER_TERM = "firewall";
	public static final String DEFAULT_SOURCE_RANGE = "10.0.0.0/8";
	public static final String DEFAULT_IP_PROTOCOL = "icmp";

	//Keys
	public static final String KEY_NAME = "";

	/**
	 * Create {@link Firewall} object based on {@link com.google.api.services.compute.model.Firewall} Google object.
	 *
	 * @param googleFirewallDetails google object
	 * @param context               context
	 * @return firewall object to be created
	 * @throws CloudException
	 */
	public static Firewall toDaseinFirewall(com.google.api.services.compute.model.Firewall googleFirewallDetails, ProviderContext context)
			throws CloudException {
		if (googleFirewallDetails == null) {
			return null;
		}
		Firewall firewall = new Firewall();
		String regionId = context.getRegionId();
		if (regionId == null) {
			return null;
		}
		firewall.setRegionId(regionId);
		firewall.setAvailable(true);
		firewall.setActive(true);
		firewall.setName(googleFirewallDetails.getName());
		firewall.setProviderFirewallId(googleFirewallDetails.getName());
		firewall.setDescription(googleFirewallDetails.getDescription());
		firewall.setProviderVlanId(GoogleMethod.getResourceName(googleFirewallDetails.getNetwork(), GoogleMethod.NETWORK));

		return firewall;
	}

	/**
	 * Creates {@link com.google.api.services.compute.model.Firewall} bases on {@link FirewallCreateOptions}
	 *
	 * @param options   for the new firewall
	 * @param projectId if project where firewall to be created
	 * @return google firewall object
	 */
	public static com.google.api.services.compute.model.Firewall fromOptions(FirewallCreateOptions options, String projectId) {
		Preconditions.checkNotNull(options);

		com.google.api.services.compute.model.Firewall newFirewall = new com.google.api.services.compute.model.Firewall();
		newFirewall.setName(options.getName());
		newFirewall.setDescription(options.getDescription());
		newFirewall.setNetwork(GoogleEndpoint.NETWORK.getEndpointUrl((StringUtils.isEmpty(options.getProviderVlanId())
				? GoogleNetworks.DEFAULT : options.getProviderVlanId()), projectId));
		newFirewall.setSourceRanges(new ArrayList<String>(Arrays.asList(DEFAULT_SOURCE_RANGE)));
		com.google.api.services.compute.model.Firewall.Allowed allowed = new com.google.api.services.compute.model.Firewall.Allowed();
		allowed.setIPProtocol(DEFAULT_IP_PROTOCOL);
		newFirewall.setAllowed(new ArrayList<com.google.api.services.compute.model.Firewall.Allowed>(Arrays.asList(allowed)));

		return newFirewall;
	}

}
