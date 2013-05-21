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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
import org.dasein.cloud.network.Direction;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.network.FirewallCreateOptions;
import org.dasein.cloud.network.FirewallRule;
import org.dasein.cloud.network.FirewallSupport;
import org.dasein.cloud.network.Permission;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.network.RuleTarget;
import org.dasein.cloud.network.RuleTargetType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
/**
 * Implements the firewall services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleFirewallSupport implements FirewallSupport {
	static private final Logger logger = Google.getLogger(GoogleFirewallSupport.class);

	private Google provider = null;

	GoogleFirewallSupport(Google provider) {
		this.provider = provider;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public String authorize(String firewallId, String source,
			Protocol protocol, int beginPort, int endPort)
					throws CloudException, InternalException {
		return authorize(firewallId, Direction.INGRESS, Permission.ALLOW, RuleTarget.getCIDR(source), protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort, 0);
	}

	@Override
	public String authorize(String firewallId, Direction direction,
			String source, Protocol protocol, int beginPort, int endPort)
					throws CloudException, InternalException {
		if( direction.equals(Direction.INGRESS) ) {
			return authorize(firewallId, direction, Permission.ALLOW, RuleTarget.getCIDR(source), protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort, 0);
		}
		else {
			return authorize(firewallId, direction, Permission.ALLOW, RuleTarget.getGlobal(firewallId), protocol, RuleTarget.getCIDR(source), beginPort, endPort, 0);
		}
	}

	@Override
	public String authorize(String firewallId, Direction direction,
			Permission permission, String source, Protocol protocol,
			int beginPort, int endPort) throws CloudException,
			InternalException {


		if( direction.equals(Direction.INGRESS) ) {
			return authorize(firewallId, direction, permission, RuleTarget.getCIDR(source), protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort, 0);
		}
		else {
			return authorize(firewallId, direction, permission, RuleTarget.getGlobal(firewallId), protocol, RuleTarget.getCIDR(source), beginPort, endPort, 0);
		}
	}

	@Override
	public String authorize(String firewallId, Direction direction,
			Permission permission, String source, Protocol protocol,
			RuleTarget target, int beginPort, int endPort)
					throws CloudException, InternalException {

		if( direction.equals(Direction.INGRESS) ) {
			return authorize(firewallId, direction, permission, RuleTarget.getCIDR(source), protocol, target, beginPort, endPort, 0);
		}
		else {
			return authorize(firewallId, direction, permission, target, protocol, RuleTarget.getCIDR(source), beginPort, endPort, 0);
		}
	}

	@Override
	public String authorize(String firewallId, Direction direction,
			Permission permission, RuleTarget sourceEndpoint,
			Protocol protocol, RuleTarget destinationEndpoint, int beginPort,
			int endPort, int precedence) throws CloudException,
			InternalException {

		if( Permission.DENY.equals(permission) ) {
			throw new OperationNotSupportedException("GCE does not support DENY rules");
		}
		if( direction.equals(Direction.EGRESS) ){
			throw new OperationNotSupportedException("GCE does not support EGRESS rules");
		}


		GoogleMethod method = new GoogleMethod(provider);
		ProviderContext ctx = provider.getContext();

		JSONObject payload = new JSONObject();

		try {
			payload.put("name", firewallId);
			payload.put("network", method.getEndpoint(ctx, GoogleMethod.NETWORK) + "/" + firewallId);

			if (sourceEndpoint.getCidr() != null) {
				JSONArray sourceRanges = new JSONArray();
				sourceRanges.put(sourceEndpoint.getCidr());
				payload.put("sourceRanges", sourceRanges);
			}

			if (sourceEndpoint.getProviderVirtualMachineId() != null) {
				JSONArray sourceTags = new JSONArray();
				sourceTags.put(sourceEndpoint.getProviderVirtualMachineId());
				payload.put("sourceTags", sourceTags);
			}

			if (destinationEndpoint != null && destinationEndpoint.getProviderVirtualMachineId() != null) {
				String target = destinationEndpoint.getProviderVirtualMachineId();
				JSONArray targetTags = new JSONArray();
				targetTags.put(target);
				payload.put("targetTags", targetTags);
			}

			JSONArray allowed = new JSONArray();
			JSONObject rule = new JSONObject();
			rule.put("IPProtocol", protocol.name());

			JSONArray ports = new JSONArray();
			if (beginPort == endPort) {
				ports.put(beginPort);
			} else ports.put(beginPort + "-" + endPort);
			rule.put("ports", ports);

			allowed.put(rule);

			payload.put("allowed", allowed);

			JSONObject response = method.patch(GoogleMethod.FIREWALL + "/" + firewallId , payload);
			method.getOperationStatus(GoogleMethod.GLOBAL_OPERATION, response);

		} catch (JSONException e) {
			e.printStackTrace();
			logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
			throw new CloudException(e);
		}

		FirewallRule rule = FirewallRule.getInstance(null, firewallId, sourceEndpoint, direction, protocol, permission, destinationEndpoint, beginPort, endPort);
		return rule.getProviderRuleId();
	}

	@Override
	public String create(String name, String description)
			throws InternalException, CloudException {

		return createInVLAN(name, description, "default");
	}

	@Override
	public String create(FirewallCreateOptions options)
			throws InternalException, CloudException {

		String description = options.getDescription();
		String name = options.getName();
		String providerVlanId = options.getProviderVlanId();

		GoogleMethod method = new GoogleMethod(provider);
		ProviderContext ctx = provider.getContext();

		JSONObject payload = new JSONObject();

		try {
			name = name.replace(" ", "").replace("-", "").replace(":", "");
			payload.put("name", name.toLowerCase());
			payload.put("description", description);

			providerVlanId = method.getEndpoint(ctx, GoogleMethod.NETWORK) + "/" + providerVlanId;
			payload.put("network", providerVlanId);


			JSONArray sranges = new JSONArray();
			JSONArray allowed = new JSONArray();
			// TODO: firewall rule needs sources ranges and the allowed array. Setting to 10.0.0.0/8 & icmp as default (Vinothini) 
			sranges.put("10.0.0.0/8");
			payload.put("sourceRanges", sranges);

			JSONObject allowedObj = new JSONObject();
			allowedObj.put("IPProtocol", "icmp");
			allowed.put(allowedObj);
			payload.put("allowed", allowed);

		} catch (JSONException e) {
			e.printStackTrace();
			logger.error("JSON conversion failed with error : " + e.getLocalizedMessage());
			throw new CloudException(e);
		}

		JSONObject response = method.post(GoogleMethod.FIREWALL, payload);

		String fwName = null;

		String status = method.getOperationStatus(GoogleMethod.GLOBAL_OPERATION, response);
		if (status != null && status.equals("DONE")) {
			if( response.has("targetLink") ) {
				try {
					fwName = response.getString("targetLink");
				} catch (JSONException e) {
					e.printStackTrace();
					throw new CloudException(e);
				}
				return GoogleMethod.getResourceName(fwName, GoogleMethod.FIREWALL);
			}
		}
		return null;
	}

	@Override
	public String createInVLAN(String name, String description,
			String providerVlanId) throws InternalException, CloudException {
		FirewallCreateOptions options = FirewallCreateOptions.getInstance(providerVlanId, name, description);
		return create(options);
	}

	@Override
	public void delete(String firewallId) throws InternalException,
	CloudException {

		GoogleMethod method = new GoogleMethod(provider);

		JSONObject delResponse = method.delete(GoogleMethod.FIREWALL, new GoogleMethod.Param("id", firewallId.toLowerCase()));
		method.getOperationStatus(GoogleMethod.GLOBAL_OPERATION, delResponse); //waits till operation moves to DONE state, else throws a timeout exception

		return;

	}

	@Override
	public Firewall getFirewall(String firewallId) throws InternalException,
	CloudException {
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new CloudException("No context has been established for this request");
		}

		GoogleMethod method = new GoogleMethod(provider);
		JSONArray array = method.get(GoogleMethod.FIREWALL);

		if (array != null)
			for (int i = 0; i < array.length(); i++) {
				try {
					JSONObject firewall = (JSONObject) array.getJSONObject(i);
					if (firewall.has("name")) {
						String name = firewall.getString("name");

						if (name.equals((String) firewallId.toLowerCase())) {
							Firewall fw = toFirewall(ctx, firewall);
							return fw;
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

	private @Nullable Firewall toFirewall(@Nonnull ProviderContext ctx, @Nullable JSONObject firewallObj) throws CloudException {
		if( firewallObj == null ) {
			return null;
		}

		Firewall firewall = new Firewall();
		String regionId = ctx.getRegionId();

		if( regionId == null ) {
			return null;
		}
		firewall.setRegionId(regionId);
		firewall.setAvailable(true);
		firewall.setActive(true);
		try {
			if (firewallObj.has("name")) {
				firewall.setName(firewallObj.getString("name"));
				firewall.setProviderFirewallId(firewallObj.getString("name"));
			}
			if (firewallObj.has("description")) {
				firewall.setDescription(firewallObj.getString("description"));
			}
			if (firewallObj.has("network")) {
				firewall.setProviderVlanId(firewallObj.getString("network"));
			}

		} catch (JSONException e) {
			logger.error("Failed to parse JSON from the cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}

		return firewall;
	}



	@Override
	public String getProviderTermForFirewall(Locale locale) {
		return "firewall";
	}

	@Override
	public Collection<FirewallRule> getRules(String firewallId)
			throws InternalException, CloudException {

		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new CloudException("No context has been established for this request");
		}

		GoogleMethod method = new GoogleMethod(provider);
		JSONArray array = method.get(GoogleMethod.FIREWALL);

		if (array != null)
			for (int i = 0; i < array.length(); i++) {
				try {
					JSONObject firewall = (JSONObject) array.getJSONObject(i);
					if (firewall.has("name")) {
						String name = firewall.getString("name");

						if (firewallId.toLowerCase().contains(name)) {

							Collection<FirewallRule> fwRules = toFirewallRules(firewall);
							return fwRules;
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



	private @Nonnull Collection<FirewallRule> toFirewallRules(@Nullable JSONObject firewall) throws CloudException, InternalException {
		ArrayList<FirewallRule> rules = new ArrayList<FirewallRule>();
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new InternalException("No context was established");
		} 
		try {

			String providerFirewallId = null;

			if (firewall.has("name")) {
				providerFirewallId = firewall.getString("name");
			}
			List<String> sources = getIPsForTags(firewall, "sourceTags");
			List<String> targets = getIPsForTags(firewall, "targetTags");

			if (firewall.has("sourceRanges")) {
				JSONArray sourceArray = firewall.getJSONArray("sourceRanges");
				for (int i = 0; i < sourceArray.length(); i++) {
					String source = sourceArray.getString(i);
					sources.add(source);
				}
			}

			FirewallRule rule;

			if (firewall.has("allowed")) {
				JSONArray allowed = firewall.getJSONArray("allowed");
				for (int j = 0; j < allowed.length(); j++) {
					JSONObject allowedObj = allowed.getJSONObject(j);
					String protocol = null;
					if (allowedObj.has("IPProtocol")) { 
						protocol = allowedObj.getString("IPProtocol");
					}
					if (allowedObj.has("ports")) {

						JSONArray ports = allowedObj.getJSONArray("ports");
						for (int k = 0; k < ports.length(); k++) {
							String port = ports.getString(k);
							for (String source: sources) {

								// for every port source add the firewall rule
								int startPort = Integer.parseInt(port);
								int endPort = Integer.parseInt(port);
								if (port.contains("-")) {
									String[] temp = port.split("-");
									startPort = Integer.parseInt(temp[0]);
									endPort = Integer.parseInt(temp[1]);;
								}
								if (targets == null || targets.size() == 0) {
									String network = firewall.getString("network");
									int indexNetwork = network.lastIndexOf("/");
									String networkId = network.substring(indexNetwork + 1, network.length());

									rule = FirewallRule.getInstance(null, providerFirewallId, RuleTarget.getCIDR(source), Direction.INGRESS, 
											Protocol.valueOf(protocol.toUpperCase()), Permission.ALLOW, RuleTarget.getGlobal(networkId), startPort, endPort);
									rules.add(rule);

								} else {
									for (String target: targets) {
										RuleTarget ruleTarget = RuleTarget.getGlobal(target);

										rule = FirewallRule.getInstance(null, providerFirewallId, RuleTarget.getCIDR(source), Direction.INGRESS, Protocol.valueOf(protocol.toUpperCase()), Permission.ALLOW, ruleTarget, startPort, endPort);
										rules.add(rule);
									}
								}
							}
						}
					}
				}
			}								    

		} catch (JSONException e) {
			logger.error("Failed to parse JSON from the cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		} catch (InternalException e) {
			logger.error("Failed to get firewall rule from the cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		} catch (Exception e) {
			logger.error("Failed to get firewall rule from the cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}

		return rules;		
	}

	private @Nullable List<String> getIPsForTags(JSONObject firewall, String tagName) throws JSONException, CloudException, InternalException {
		List<String> ips = new ArrayList<String>();

		if (firewall.has(tagName)) {
			JSONArray targetArray = firewall.getJSONArray(tagName);
			for (int i = 0; i < targetArray.length(); i++) {
				String targetTag = targetArray.getString(i);
				GoogleMethod method = new GoogleMethod(provider);
				JSONArray servers = method.get(GoogleMethod.SERVER); // filter to get tags[] is not supported
				if (servers != null) {
					for (int i1 = 0; i1 < servers.length(); i1++) {
						JSONObject server = servers.getJSONObject(i1);
						if (server.has("tags")) {
							JSONArray tags = server.getJSONArray("tags");
							for (int tagIndex = 0; tagIndex < tags.length(); tagIndex++) {
								String tag = tags.getString(tagIndex);
								if (!tag.equals(targetTag)) {
									if (server.has("networkInterfaces")) {
										JSONArray nwInterfaces = server.getJSONArray("networkInterfaces");
										for (int l = 0; l < nwInterfaces.length(); l++) {
											JSONObject nw = nwInterfaces.getJSONObject(l);
											if (nw.has("networkIP")) {
												String networkIP = nw.getString("networkIP");
												ips.add(networkIP);
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return ips;
	}


	@Override
	public Requirement identifyPrecedenceRequirement(boolean inVlan)
			throws InternalException, CloudException {
		return Requirement.NONE;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean isZeroPrecedenceHighest() throws InternalException,
	CloudException {
		throw new OperationNotSupportedException("Firewall rule precedence is not supported.");
	}

	@Override
	public Collection<Firewall> list() throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new CloudException("No context has been established for this request");
		}

		ArrayList<Firewall> list = new ArrayList<Firewall>();
		GoogleMethod method = new GoogleMethod(provider);


		JSONArray firewalls = method.get(GoogleMethod.FIREWALL);
		if (firewalls != null)
			for ( int i = 0; i < firewalls.length(); i++) {
				JSONObject firewall = null;
				try {
					firewall = firewalls.getJSONObject(i);
					Firewall fw = toFirewall(ctx, firewall);
					if (fw != null) {
						list.add(fw);
					}
				} catch (JSONException e) {
					logger.error("JSON Parser error");
					e.printStackTrace();
					throw new CloudException(e);
				}
			}
		return list;

	}

	@Override
	public Iterable<ResourceStatus> listFirewallStatus()
			throws InternalException, CloudException {
		Collection<ResourceStatus> firewallStatus = new ArrayList<ResourceStatus>();
		firewallStatus.add(new ResourceStatus(provider.getContext().getRegionId(), true));
		return firewallStatus;

	}

	@Override
	public Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan)
			throws InternalException, CloudException {
		Collection<RuleTargetType> destTypes = new ArrayList<RuleTargetType>();
		if (!inVlan) {
			destTypes.add(RuleTargetType.VLAN);
			destTypes.add(RuleTargetType.CIDR);
			destTypes.add(RuleTargetType.GLOBAL);
		}
		return destTypes;
	}

	@Override
	public Iterable<Direction> listSupportedDirections(boolean inVlan)
			throws InternalException, CloudException {
		Collection<Direction> directions = new ArrayList<Direction>();
		if (!inVlan) {
			directions.add(Direction.INGRESS);
		}
		return directions;
	}

	@Override
	public Iterable<Permission> listSupportedPermissions(boolean inVlan)
			throws InternalException, CloudException {
		Collection<Permission> permissions = new ArrayList<Permission>();
		if (!inVlan) {
			permissions.add(Permission.ALLOW);
			//			permissions.add(Permission.DENY);
		}
		return permissions;
	}

	@Override
	public Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan)
			throws InternalException, CloudException {
		Collection<RuleTargetType> sourceTypes = new ArrayList<RuleTargetType>();
		if (!inVlan) {
			sourceTypes.add(RuleTargetType.CIDR);
			sourceTypes.add(RuleTargetType.VLAN);
		}
		return sourceTypes;
	}

	@Override
	public void removeTags(String firewallId, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support firewall tags");

	}

	@Override
	public void removeTags(String[] firewallIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support firewall tags");

	}

	@Override
	public void revoke(String providerFirewallRuleId) throws InternalException,
	CloudException {

		FirewallRule firewallRule = FirewallRule.parseId(providerFirewallRuleId);
		String source = providerFirewallRuleId.split(":")[3];

		revoke(firewallRule.getFirewallId(), firewallRule.getDirection(), source, firewallRule.getProtocol(), firewallRule.getStartPort(), firewallRule.getEndPort());

	}

	@Override
	public void revoke(String firewallId, String source, Protocol protocol,
			int beginPort, int endPort) throws CloudException,
			InternalException {
		revoke(firewallId,  Direction.INGRESS, source, protocol, beginPort, endPort);

	}

	@Override
	public void revoke(String firewallId, Direction direction, String source,
			Protocol protocol, int beginPort, int endPort)
					throws CloudException, InternalException {
		revoke(firewallId,  direction, Permission.DENY, source, protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort);

	}

	@Override
	public void revoke(String firewallId, Direction direction,
			Permission permission, String source, Protocol protocol,
			int beginPort, int endPort) throws CloudException,
			InternalException {
		revoke(firewallId,  direction, permission, source, protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort);

	}

	@Override
	public void revoke(String firewallId, Direction direction,
			Permission permission, String source, Protocol protocol,
			RuleTarget target, int beginPort, int endPort)
					throws CloudException, InternalException {
		JSONObject tempObj = null;
		GoogleMethod method = new GoogleMethod(provider);
		JSONArray response = method.get(GoogleMethod.FIREWALL + "/" + firewallId);

		if (response != null) {
			try {

				JSONObject firewall = response.getJSONObject(0);
				JSONObject tempFirewall =  new JSONObject();

				// revoke the cidr
				if (firewall.has("sourceRanges")) {

					JSONArray sourceRanges = firewall.getJSONArray("sourceRanges");
					firewall.remove("sourceRanges");
					JSONArray temp = new JSONArray();
					for (int i = 0; i < sourceRanges.length(); i++)
						if (!sourceRanges.getString(i).equals(source)) {
							temp.put(sourceRanges.getString(i));
						}

					if (temp.length() > 0) {
						firewall.put("sourceRanges", temp);
					}
				}
				if (!firewall.has("sourceRanges")) {
					JSONArray temp = new JSONArray();
					temp.put("10.0.0.0/8");
					firewall.put("sourceRanges", temp);
				}
				if (firewall.has("sourceTags")) {

					JSONArray sourceRanges = firewall.getJSONArray("sourceTags");
					firewall.remove("sourceTags");
					JSONArray temp = new JSONArray();
					for (int i = 0; i < sourceRanges.length(); i++)
						if (!sourceRanges.getString(i).equals(source)) {
							temp.put(sourceRanges.getString(i));
						}

					if (temp.length() > 0) {
						firewall.put("sourceTags", temp);
					}
				}

				if (firewall.has("targetTags")) {
					JSONArray targetRanges = firewall.getJSONArray("targetTags");
					firewall.remove("targetTags");
					JSONArray temp = new JSONArray();
					for (int i = 0; i < targetRanges.length(); i++)
						if (!targetRanges.getString(i).equals(target.getCidr())) {
							temp.put(targetRanges.getString(i));
						}
					if (temp.length() > 0) {
						firewall.put("targetTags", temp);
					}
				}

				tempFirewall = firewall;

				// revoke the protocol
				if (firewall.has("allowed")) {
					JSONArray allowedArray = firewall.getJSONArray("allowed");
					JSONArray temp = new JSONArray();
					for (int i = 0; i < allowedArray.length(); i++) {

						JSONObject allowed = allowedArray.getJSONObject(i);
						if (allowed.has("IPProtocol")) {
							// if allowed list is present, then IPProtocol is a required field and ports is optional

							String protocolName = allowed.getString("IPProtocol");
							if (protocolName.toLowerCase().equals(protocol.name().toString().toLowerCase())) {
								if (allowed.has("ports")) {
									String bPort = String.valueOf(beginPort);
									String ToPort = endPort == -1 ? String.valueOf(beginPort) : String.valueOf(endPort);
									JSONArray ports = allowed.getJSONArray("ports");
									JSONArray portsTemp = new JSONArray();
									for (int k = 0; k < ports.length(); k++) {
										String port = ports.getString(k);
										if (!port.equals(bPort) && !port.equals(bPort+"-"+ToPort)) {
											portsTemp.put(port);
										}
									}
									try {
										if (portsTemp.length() > 0) {
											JSONObject tempObject = new JSONObject();
											tempObject.put("ports", portsTemp);
											tempObject.put("IPProtocol", "tcp");
											temp.put(tempObject);
										}
									} catch (Exception e) {
										e.printStackTrace();
										logger.error("JSON parser error");
										throw new CloudException(e);
									}
								}
							} else {

								temp.put(allowed);
							}
						}
					}
					tempFirewall.remove("allowed");
					if (temp.length() > 0) {
						tempFirewall.put("allowed", temp);
					}
				}
				JSONObject patchResponse = method.patch(GoogleMethod.FIREWALL + "/" + firewallId, tempFirewall);
				method.getOperationStatus(GoogleMethod.GLOBAL_OPERATION, patchResponse);

			} catch (JSONException e) {
				logger.error("Failed to parse JSON from the cloud: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e + " payload " +tempObj.toString());
			}
		}
	}

	@Override
	public boolean supportsRules(Direction direction, Permission permission,
			boolean inVlan) throws CloudException, InternalException {
		return (permission.equals(Permission.ALLOW) && direction.equals(Direction.INGRESS));
	}

	@Override
	public boolean supportsFirewallCreation(boolean inVlan)
			throws CloudException, InternalException {
		return true;
	}

	@Override
	public boolean supportsFirewallSources() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public void updateTags(String firewallId, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google firewall does not contain meta data");

	}

	@Override
	public void updateTags(String[] firewallIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google firewall does not contain meta data");

	}
}
