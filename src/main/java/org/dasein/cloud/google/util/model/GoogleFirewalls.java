package org.dasein.cloud.google.util.model;

import com.google.api.client.util.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.network.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.api.services.compute.model.Firewall.Allowed;

/**
 * @author Eduard Bakaev
 * @since 13.12.2013
 */
public final class GoogleFirewalls {

	public static final String PROVIDER_TERM = "firewall";
	public static final String DEFAULT_SOURCE_RANGE = "10.0.0.0/8";

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
		firewall.setProviderVlanId(GoogleEndpoint.NETWORK.getResourceFromUrl(googleFirewallDetails.getNetwork()));
		firewall.setRules(toDaseinFirewallRules(googleFirewallDetails));

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
		Preconditions.checkArgument(!options.getAuthorizeRules().isEmpty(), "No default firewall rule is provided");

		com.google.api.services.compute.model.Firewall newFirewall = new com.google.api.services.compute.model.Firewall();
		newFirewall.setName(options.getName());
		newFirewall.setDescription(options.getDescription());
		newFirewall.setNetwork(GoogleEndpoint.NETWORK.getEndpointUrl((StringUtils.isEmpty(options.getProviderVlanId())
				? GoogleNetworks.DEFAULT : options.getProviderVlanId()), projectId));

		for (FirewallRule firewallRule : options.getAuthorizeRules()) {
			applyInboundFirewallRule(newFirewall, firewallRule);
		}

		newFirewall.setTargetTags(new ArrayList<String>(options.getTargetLabels()));

		return newFirewall;
	}

	public static void applyInboundFirewallRule(com.google.api.services.compute.model.Firewall googleFirewall, FirewallRule inboundRule) {
		applyInboundFirewallRule(googleFirewall, inboundRule.getSourceEndpoint(), inboundRule.getProtocol(),
				inboundRule.getStartPort(), inboundRule.getEndPort());
	}

	public static void applyInboundFirewallRule(com.google.api.services.compute.model.Firewall googleFirewall,
												RuleTarget sourceEndpoint, Protocol protocol, int beginPort, int endPort) {
		List<Allowed> allowedList = googleFirewall.getAllowed() != null ? googleFirewall.getAllowed() : new ArrayList<Allowed>();
		if (sourceEndpoint.getCidr() != null) {
			List<String> sourceRanges = googleFirewall.getSourceRanges() == null ? new ArrayList<String>()
					: googleFirewall.getSourceRanges();
			sourceRanges.add(sourceEndpoint.getCidr());
			googleFirewall.setSourceRanges(sourceRanges);
		}

		// either port ranges (CIDR) or source (FIREWALL) are accepted
		if (sourceEndpoint.getProviderFirewallId() != null) {
			List<String> sourceTags = googleFirewall.getSourceTags() == null ? new ArrayList<String>() : googleFirewall.getSourceTags();
			sourceTags.add(sourceEndpoint.getProviderFirewallId());
			googleFirewall.setSourceTags(sourceTags);
		} else {
			Allowed allowed = getAllowed(protocol, beginPort, endPort);
			allowedList.add(allowed);
			googleFirewall.setAllowed(allowedList);
		}
	}

	/**
	 * Retrieves {@link Allowed} object
	 *
	 * @param protocol  value to use
	 * @param beginPort value
	 * @param endPort   value
	 * @return {@link Allowed} object
	 */
	public static Allowed getAllowed(Protocol protocol, int beginPort, int endPort) {
		Allowed allowed = new Allowed();
		allowed.setIPProtocol(protocol.name());
		List<String> ports = new ArrayList<String>();
		if (beginPort == endPort) {
			ports.add(String.valueOf(beginPort));
		} else {
			ports.add(beginPort + "-" + endPort);
		}
		if (!Protocol.ICMP.equals(protocol)) {
			allowed.setPorts(ports);
		}
		return allowed;
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
			List<String> sources = new ArrayList<String>();
			List<String> targets = firewall.getTargetTags() == null ? new ArrayList<String>() : firewall.getTargetTags();

			if (firewall.getSourceRanges() != null) {
				List<String> sourceRanges = firewall.getSourceRanges();
				for (String sRange : sourceRanges) {
					sources.add(sRange);
				}
			}

			FirewallRule rule;
			if (firewall.getAllowed() != null) {
				List<Allowed> allowed = firewall.getAllowed();
				for (Allowed allowedObj : allowed) {
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
								} else {
									startPort = Integer.valueOf(port);
									endPort = startPort;
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

			if (firewall.getSourceTags() != null && firewall.getSourceTags().size() > 0) {
				for (String sourceTag : firewall.getSourceTags()) {
					rule = FirewallRule.getInstance(null, providerFirewallId, RuleTarget.getGlobal(sourceTag), Direction.INGRESS,
							Protocol.ANY, Permission.ALLOW, RuleTarget.getGlobal(sourceTag), 22, 22);
					rules.add(rule);
				}
			}
		}

		return rules;
	}

}
