package org.dasein.cloud.google.util.model;

import com.google.api.client.util.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.network.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

	/**
	 * Creates list of {@link FirewallRule} bases on {@link com.google.api.services.compute.model.Firewall}
	 *
	 * @param firewall google firewall to convert
	 * @return list of rules for provided firewall
	 */
	public static Collection<FirewallRule> toDaseinFirewallRules(com.google.api.services.compute.model.Firewall firewall) {
		ArrayList<FirewallRule> rules = new ArrayList<FirewallRule>();
		String providerFirewallId = null;

		if (firewall != null) {
			providerFirewallId = firewall.getName();
			List<String> sources = firewall.getSourceTags() == null ? new ArrayList<String>() : firewall.getSourceTags();
			List<String> targets = firewall.getTargetTags() == null ? new ArrayList<String>() : firewall.getTargetTags();

			if (firewall.getSourceRanges() != null) {
				List<String> sourceRanges = firewall.getSourceRanges();
				for (String sRange : sourceRanges) {
					sources.add(sRange);
				}
			}

			FirewallRule rule;
			if (firewall.getAllowed() != null) {
				List<com.google.api.services.compute.model.Firewall.Allowed> allowed = firewall.getAllowed();
				for (com.google.api.services.compute.model.Firewall.Allowed allowedObj : allowed) {
					String protocol = null;
					if (allowedObj.getIPProtocol() != null) {
						protocol = allowedObj.getIPProtocol();
					}
					if (allowedObj.getPorts() != null) {
						List<String> ports = allowedObj.getPorts();
						for (String port : ports) {
							for (String source : sources) {
								// for every port source add the firewall rule
								int startPort = 0;
								int endPort = 0;
								if (port.contains("-")) {
									String[] temp = port.split("-");
									startPort = Integer.parseInt(temp[0]);
									endPort = Integer.parseInt(temp[1]);
								}
								if (targets == null || targets.size() == 0) {
									String network = firewall.getNetwork();
									String networkId = network.substring(network.lastIndexOf("/") + 1);
									rule = FirewallRule.getInstance(null, providerFirewallId, RuleTarget.getCIDR(source), Direction.INGRESS,
											Protocol.valueOf(protocol.toUpperCase()), Permission.ALLOW, RuleTarget.getGlobal(networkId), startPort, endPort);
									rules.add(rule);
								} else {
									for (String target : targets) {
										RuleTarget ruleTarget = RuleTarget.getGlobal(target);
										rule = FirewallRule.getInstance(null, providerFirewallId, RuleTarget.getCIDR(source), Direction.INGRESS,
												Protocol.valueOf(protocol.toUpperCase()), Permission.ALLOW, ruleTarget, startPort, endPort);
										rules.add(rule);
									}
								}
							}
						}
					}
				}
			}
		}

		return rules;
	}

	/**
	 * Retrieves {@link com.google.api.services.compute.model.Firewall.Allowed} object
	 *
	 * @param protocol value to use
	 * @param beginPort value
	 * @param endPort value
	 * @return {@link com.google.api.services.compute.model.Firewall.Allowed} object
	 */
	public static com.google.api.services.compute.model.Firewall.Allowed getAllowed(Protocol protocol, int beginPort, int endPort) {
		com.google.api.services.compute.model.Firewall.Allowed allowed = new com.google.api.services.compute.model.Firewall.Allowed();
		allowed.setIPProtocol(protocol.name());
		List<String> ports = new ArrayList<String>();
		if (beginPort == endPort) {
			ports.add(String.valueOf(beginPort));
		} else {
			ports.add(beginPort + "-" + endPort);
		}
		allowed.setPorts(ports);
		return allowed;
	}

}
