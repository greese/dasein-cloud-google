/**
 * Copyright (C) 2012-2014 Dell, Inc
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEFirewallCapabilities;
import org.dasein.cloud.network.AbstractFirewallSupport;
import org.dasein.cloud.network.Direction;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.network.FirewallConstraints;
import org.dasein.cloud.network.FirewallCreateOptions;
import org.dasein.cloud.network.FirewallRule;
import org.dasein.cloud.network.Permission;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.network.RuleTarget;
import org.dasein.cloud.network.RuleTargetType;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.util.APITrace;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Firewall.Allowed;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Operation;

/**
 * Implements the firewall services supported in the Google API.
 * @author Drew Lyall
 * @version 2014.05 refactor
 * @since 2014.03
 */
public class FirewallSupport extends AbstractFirewallSupport{
    static private final Logger logger = Google.getLogger(org.dasein.cloud.network.FirewallSupport.class);

    private Google provider = null;

    FirewallSupport(Google provider) {
        super(provider);
        this.provider = provider;
    }
    @Override
    public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull RuleTarget sourceEndpoint, @Nonnull Protocol protocol, @Nonnull RuleTarget destinationEndpoint, int beginPort, int endPort, int precedence) throws CloudException, InternalException {
        APITrace.begin(provider, "Firewall.authorize");
        try {
            if (Permission.DENY.equals(permission)) {
                throw new OperationNotSupportedException("GCE does not support DENY rules");
            }
            if (direction.equals(Direction.EGRESS)) {
                throw new OperationNotSupportedException("GCE does not support EGRESS rules");
            }
            Compute gce = provider.getGoogleCompute();
            com.google.api.services.compute.model.Firewall googleFirewall = new com.google.api.services.compute.model.Firewall();

            Random r = new Random();
            char c = (char)(r.nextInt(26) + 'a');
            googleFirewall.setName(c + UUID.randomUUID().toString());
            if(protocol == Protocol.ICMP)
                googleFirewall.setDescription(sourceEndpoint.getCidr() + ":" + protocol.name()); //  + ":" + beginPort + "-" + endPort);
            else
                googleFirewall.setDescription(sourceEndpoint + ":" + protocol.name() + ":" + beginPort + "-" + endPort);
            VLAN vlan = provider.getNetworkServices().getVlanSupport().getVlan(firewallId.split("fw-")[1]);
            googleFirewall.setNetwork(vlan.getTag("contentLink"));

            String portString = "";
            if (beginPort == endPort)
                portString = beginPort + "";
            else {
                portString = beginPort + "-" + endPort;
            }
            ArrayList<Allowed> allowedRules = new ArrayList<Allowed>();
            Allowed allowed = new Allowed();
            allowed.setIPProtocol(protocol.name());
            if (protocol != Protocol.ICMP)
                allowed.setPorts(Collections.singletonList(portString));
            allowedRules.add(allowed);
            googleFirewall.setAllowed(allowedRules);

            if (sourceEndpoint.getRuleTargetType().equals(RuleTargetType.VLAN) || sourceEndpoint.getRuleTargetType().equals(RuleTargetType.GLOBAL)){
                throw new OperationNotSupportedException("GCE does not support VLAN or GLOBAL as valid source types");
            }
            if (sourceEndpoint.getRuleTargetType().equals(RuleTargetType.VM)){
                googleFirewall.setSourceTags(Collections.singletonList(sourceEndpoint.getProviderVirtualMachineId()));
            } else if(sourceEndpoint.getRuleTargetType().equals(RuleTargetType.CIDR)){
                googleFirewall.setSourceRanges(Collections.singletonList(sourceEndpoint.getCidr()));
            }

            if (destinationEndpoint.getRuleTargetType().equals(RuleTargetType.VM)){
                googleFirewall.setTargetTags(Collections.singletonList(destinationEndpoint.getProviderVirtualMachineId()));
            }
            else if ((!destinationEndpoint.getRuleTargetType().equals(RuleTargetType.VLAN)) && (protocol != Protocol.ICMP)) { // remove the !
                throw new OperationNotSupportedException("GCE only supports either specific VMs or the whole network as a valid destination type");
            }

            Collection<FirewallRule> existingRules = this.getRules(firewallId);
            boolean ruleDiffers = true;
            String sourceEndpointCidr = sourceEndpoint.getCidr();

            for (FirewallRule candidateRule : existingRules) {
                boolean protocolMatch   = candidateRule.getProtocol() == protocol;
                boolean startPortMatch  = candidateRule.getStartPort() == beginPort;
                boolean endPortMatch    = candidateRule.getEndPort() == endPort;
                boolean sourceCidrMatch       = ((sourceEndpoint.equals(candidateRule.getCidr().toString())) || 
                                                 (sourceEndpoint.toString().equals("CIDR:" + candidateRule.getCidr().toString()))|| 
                                                 (sourceEndpoint.toString().equals("VM:" + candidateRule.getCidr().toString())));
                boolean endpointCidrMatch       = ((sourceEndpointCidr == null) || (sourceEndpoint.getCidr().equals(candidateRule.getSourceEndpoint())));
                boolean directionMatch  = candidateRule.getDirection() == direction;

                if (protocol == Protocol.ICMP) {
                    if (protocolMatch && sourceCidrMatch) {
                        ruleDiffers = false;
                    } else {
                        if (protocolMatch && directionMatch && startPortMatch && endPortMatch && sourceCidrMatch) {
                            ruleDiffers = false;
                        }
                    }
                }
                if (protocolMatch && directionMatch && startPortMatch && endPortMatch && sourceCidrMatch) {
                    ruleDiffers = false;
                }
            }
            if (ruleDiffers == false)
                throw new CloudException("Duplicate rule already exists");

                try {
                    Operation job = gce.firewalls().insert(provider.getContext().getAccountNumber(), googleFirewall).execute();
                    GoogleMethod method = new GoogleMethod(provider);
                    return method.getOperationTarget(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "", false);
                } catch (IOException ex) {
                    logger.error(ex.getMessage());
                    if (ex.getClass() == GoogleJsonResponseException.class) {
                        GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                        throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                    } else
                        throw new CloudException("An error occurred creating a new rule on " + firewallId + ": " + ex.getMessage());
                }

        } finally{
            APITrace.end();
        }
    }

    @Override
    public @Nonnull String create(@Nonnull FirewallCreateOptions options)throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not allow the creation/deletion of firewalls");
    }

    private transient volatile GCEFirewallCapabilities capabilities;
    @Override
    public @Nonnull GCEFirewallCapabilities getCapabilities() throws CloudException, InternalException{
        if (capabilities == null){
            capabilities = new GCEFirewallCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public void delete(@Nonnull String s) throws InternalException, CloudException{
        throw new OperationNotSupportedException("GCE does not allow the creation/deletion of firewalls");
    }

    @Override
    public Firewall getFirewall(@Nonnull String firewallId) throws InternalException, CloudException {
        if (!firewallId.startsWith("fw-"))
            return null;
        ProviderContext ctx = provider.getContext();
        if ( ctx == null ) {
            throw new CloudException("No context has been established for this request");
        }

        Compute gce = provider.getGoogleCompute();
        try {
            Network firewall = gce.networks().get(ctx.getAccountNumber(), firewallId.split("fw-")[1]).execute();
            List<com.google.api.services.compute.model.Firewall> rules = gce.firewalls().list(ctx.getAccountNumber()).setFilter("network eq .*/" + firewall.getName()).execute().getItems();
            return toFirewall(firewall, rules);
        } catch (IOException ex) {
            logger.error("An error occurred while getting firewall " + firewallId + ": " + ex.getMessage());
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
               throw new CloudException(ex.getMessage());
        }
    }

    @Override
    public @Nonnull Collection<FirewallRule> getRules(@Nonnull String firewallId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();
        if ( ctx == null ) {
            throw new CloudException("No context has been established for this request");
        }

        Compute gce = provider.getGoogleCompute();
        try{
            List<com.google.api.services.compute.model.Firewall> rules = gce.firewalls().list(ctx.getAccountNumber()).setFilter("network eq .*" + firewallId.split("fw-")[1]).execute().getItems();
            if(rules != null) {
                return toFirewallRules(rules);
            } else 
                return Collections.emptyList();
        } catch (IOException ex) {
            logger.error("An error occurred while getting firewall " + firewallId + ": " + ex.getMessage());
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
    public @Nonnull Collection<Firewall> list() throws InternalException, CloudException{
        //GCE has a defacto Firewall for every network so will simply map a fake firewall to networks.
        ProviderContext ctx = provider.getContext();
        if ( ctx == null )
            throw new InternalException("No context was established");

        ArrayList<Firewall> firewalls = new ArrayList<Firewall>();
        try {
            Compute gce = provider.getGoogleCompute();
            List<Network> networks = gce.networks().list(ctx.getAccountNumber()).execute().getItems();
            List<com.google.api.services.compute.model.Firewall> rules = gce.firewalls().list(ctx.getAccountNumber()).execute().getItems(); //.setFilter("network eq " + network.getName())
            if (networks != null && networks.size() > 0)
                for (Network network : networks) {
                    List<com.google.api.services.compute.model.Firewall> rulesSubset = new ArrayList <com.google.api.services.compute.model.Firewall>();
                    for (com.google.api.services.compute.model.Firewall rule : rules)
                        if (rule.getNetwork().equals(network.getSelfLink()))
                            rulesSubset.add(rule);

                    if (network != null) {
                        Firewall firewall = toFirewall(network, rulesSubset);
                        if (firewall != null)
                            firewalls.add(firewall);
                    }
                }
        } catch (IOException ex) {
            logger.error(ex.getMessage());
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred while listing Firewalls: " + ex.getMessage());
        }
        return firewalls;
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listFirewallStatus() throws InternalException, CloudException {
        ArrayList<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
        Collection<Firewall> firewalls = list();
        for (Firewall firewall : firewalls) {
            ResourceStatus status = new ResourceStatus(firewall.getProviderFirewallId(), true);
            statuses.add(status);
        }
        return statuses;
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan) throws InternalException, CloudException {
        Collection<RuleTargetType> destinationTypes = new ArrayList<RuleTargetType>();
        destinationTypes.add(RuleTargetType.VM);
        return destinationTypes;
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<Direction> listSupportedDirections(boolean inVlan) throws InternalException, CloudException {
        Collection<Direction> directions = new ArrayList<Direction>();
        directions.add(Direction.INGRESS);
        return directions;
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<Permission> listSupportedPermissions(boolean inVlan)throws InternalException, CloudException {
        Collection<Permission> permissions = new ArrayList<Permission>();
        permissions.add(Permission.ALLOW);
        return permissions;
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan) throws InternalException, CloudException {
        Collection<RuleTargetType> sourceTypes = new ArrayList<RuleTargetType>();
        sourceTypes.add(RuleTargetType.CIDR);
        sourceTypes.add(RuleTargetType.VM);
        return sourceTypes;
    }

    @Override
    public void revoke(@Nonnull String providerFirewallRuleId) throws InternalException, CloudException {
        APITrace.begin(provider, "Firewall.revoke");
        try {
            try {
                Compute gce = provider.getGoogleCompute();
                if (providerFirewallRuleId.contains("--")) {
                    String[] parts = providerFirewallRuleId.split("\\-\\-");
                    String firewall = parts[0];
                    com.google.api.services.compute.model.Firewall fw = gce.firewalls().get(provider.getContext().getAccountNumber(), firewall).execute();
                    List<String> sourceRanges = fw.getSourceRanges();
                    for (String sourceRange : sourceRanges) {
                        if (parts[1].equals(sourceRange)) {
                            sourceRanges.remove(sourceRange);
                            break;
                        }
                    }
                    fw.setSourceRanges(sourceRanges);
                    Operation job = gce.firewalls().update(provider.getContext().getAccountNumber(), firewall, fw).execute();
                    GoogleMethod method = new GoogleMethod(provider);
                    if (!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "")) {
                        throw new CloudException("An error occurred deleting the rule: Operation Timed Out");
                    }
                } else {
                    Operation job = gce.firewalls().delete(provider.getContext().getAccountNumber(), providerFirewallRuleId).execute();
                    GoogleMethod method = new GoogleMethod(provider);
                    if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "")) {
                        throw new CloudException("An error occurred deleting the rule: Operation Timed Out");
                    }
                }
            } catch (IOException ex) {
                logger.error(ex.getMessage());
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException("An error occurred while deleting the firewall rule: " + ex.getMessage());
             }
        }
        finally{
            APITrace.end();
        }
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        revoke(firewallId, Direction.INGRESS, source, protocol, beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        if (!direction.equals(Direction.INGRESS))
            throw new InternalException("GCE does not support outbound firewall rules");
        revoke(firewallId, Direction.INGRESS, Permission.ALLOW, source, protocol, beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        if (!direction.equals(Direction.INGRESS))
            throw new InternalException("GCE does not support outbound firewall rules");
        if (!permission.equals(Permission.ALLOW))
            throw new InternalException("GCE does not support deny firewall rules");
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, @Nonnull RuleTarget target, int beginPort, int endPort) throws CloudException, InternalException {
        if (!direction.equals(Direction.INGRESS))
            throw new InternalException("GCE does not support outbound firewall rules");
        if (!permission.equals(Permission.ALLOW))
            throw new InternalException("GCE does not support deny firewall rules");

        if (source.contains("/")) {
            String[] parts = source.split("/");
            if (!InetAddressUtils.isIPv4Address(parts[0]))
                throw new InternalException("GCE only supports valid IPv4 addresses or cidrs as source targets");
        } else {
            if (!InetAddressUtils.isIPv4Address(source))
                throw new InternalException("GCE only supports valid IPv4 addresses or cidrs as source targets");
            else 
                source = source + "/32";
        }

        if (!target.getRuleTargetType().equals(RuleTargetType.VM) && !target.getRuleTargetType().equals(RuleTargetType.VLAN))throw new InternalException("GCE only supports VM or VLAN targets for firewall rules");

        FirewallRule rule = null;
        for (FirewallRule current : getRules(firewallId)) {
            if (!current.getSourceEndpoint().getCidr().equals(source))
                continue;
            if (!current.getProtocol().equals(protocol))
                continue;
            if (current.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.VM)){
                if (!current.getDestinationEndpoint().getProviderVirtualMachineId().equals(target.getProviderVirtualMachineId()))
                    continue;
            } else if (current.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.VLAN)) {
                if (!current.getDestinationEndpoint().getProviderVlanId().equals(target.getProviderVlanId()))
                    continue;
            }
            if (current.getStartPort() != beginPort)
                continue;
            if (current.getEndPort() != endPort)
                continue;

            rule = current;
        }
        if (rule == null)
            throw new InternalException("The rule for " + direction.name() + ", " + permission.name() + ", " + source + ", " + beginPort + "-" + endPort + " does not exist");

        revoke(rule.getProviderRuleId());
    }

    private @Nullable Firewall toFirewall(@Nonnull Network googleFirewall, @Nullable List<com.google.api.services.compute.model.Firewall> rules){
        Firewall firewall = new Firewall();
        //firewall.setProviderFirewallId(googleFirewall.getId() + "");// - GCE uses name as ID
        firewall.setProviderFirewallId("fw-" + googleFirewall.getName());
        //firewall.setRegionId(provider.getContext().getRegionId());//In GCE networks and therefore firewalls do not have regions
        firewall.setVisibleScope(VisibleScope.ACCOUNT_GLOBAL);
        firewall.setAvailable(true);
        firewall.setActive(true);
        firewall.setName(googleFirewall.getName() + " Firewall");
        firewall.setDescription(googleFirewall.getDescription());
        firewall.setProviderVlanId(googleFirewall.getName());
        if(rules != null) {
            firewall.setRules(toFirewallRules(rules));
        }

        return firewall;
    }

    /**
     * The Dasein abstraction assumes that a firewall rule has only one source target, one destination target, one protocol and one distinct port range
     * However, the GCE abstraction defines groups of these against one single firewall rule.
     * Therefore, Dasein splits these groups into distinct parts. The result is that, when Dasein creates rules on GCE they will matchup but when
     * Dasein is reading rules created on the GCE side there is the potential for each one to map to multiple Dasein rules.
     * @param rules the GCE API rule response
     * @return A Dasein collection of FirewallRules
     */
    private @Nonnull Collection<FirewallRule> toFirewallRules(@Nonnull List<com.google.api.services.compute.model.Firewall> rules){
        ArrayList<FirewallRule> firewallRules = new ArrayList<FirewallRule>();
        for(com.google.api.services.compute.model.Firewall googleRule : rules) {
            List<RuleTarget> sources = new ArrayList<RuleTarget>();

            if (googleRule.getSourceRanges() != null)
                for (String source : googleRule.getSourceRanges()) {
                    //Right now GCE only supports IPv4
                    if (InetAddressUtils.isIPv4Address(source)) {
                        source = source + "/32";
                    }
                    sources.add(RuleTarget.getCIDR(source));
                }
            else 
                if (googleRule.getSourceTags() != null)
                    for (String source : googleRule.getSourceTags()) {
                        sources.add(RuleTarget.getVirtualMachine(source));
                    }
                else
                    return firewallRules; //got nothing...

            for (RuleTarget sourceTarget : sources) {
                String tail = "";
                if (sources.size() > 1)
                    tail = "--" + sourceTarget.getCidr();
                String vLanId = googleRule.getNetwork().substring(googleRule.getNetwork().lastIndexOf("/") + 1);

                for (Allowed allowed : googleRule.getAllowed()) {
                    Protocol protocol = Protocol.ANY;
                    try {
                        protocol = Protocol.valueOf(allowed.getIPProtocol().toUpperCase());
                    } catch (IllegalArgumentException ex) {  
                        // ignore, defaults to ANY if protocol is not supported explicitly 
                    }
                    RuleTarget destinationTarget;
                    int portStart = 0;
                    int portEnd = 0;

                    if (protocol != Protocol.ICMP) {
                        if ((null != allowed) && (null != allowed.getPorts())) {
                            for (String portString : allowed.getPorts()) {
                                if (portString.indexOf("-") > 0) {
                                    String[] parts = portString.split("-");
                                    portStart = Integer.valueOf(parts[0]);
                                    portEnd = Integer.valueOf(parts[1]);
                                } else 
                                    portStart = portEnd = Integer.valueOf(portString);

                                if (googleRule.getTargetTags() != null) {
                                    for (String targetTag : googleRule.getTargetTags()) {
                                        destinationTarget = RuleTarget.getVirtualMachine(targetTag);
                                        FirewallRule rule = FirewallRule.getInstance(googleRule.getName() + tail, "fw-" + vLanId, sourceTarget, Direction.INGRESS, protocol, Permission.ALLOW, destinationTarget, portStart, portEnd);
                                        firewallRules.add(rule);
                                    }
                                } else {
                                    destinationTarget = RuleTarget.getVlan(vLanId);
                                    FirewallRule rule = FirewallRule.getInstance(googleRule.getName() + tail, "fw-" + vLanId, sourceTarget, Direction.INGRESS, protocol, Permission.ALLOW, destinationTarget, portStart, portEnd);
                                    firewallRules.add(rule);
                                }
                            }
                        }
                    } else {
                        if (googleRule.getTargetTags() != null) {
                            for(String targetTag : googleRule.getTargetTags()) {
                                destinationTarget = RuleTarget.getVirtualMachine(targetTag);
                                FirewallRule rule = FirewallRule.getInstance(googleRule.getName() + tail, "fw-" + vLanId, sourceTarget, Direction.INGRESS, protocol, Permission.ALLOW, destinationTarget, portStart, portEnd);
                                firewallRules.add(rule);
                            }
                        } else {
                            destinationTarget = RuleTarget.getVlan(vLanId);
                            FirewallRule rule = FirewallRule.getInstance(googleRule.getName() + tail, "fw-" + vLanId, sourceTarget, Direction.INGRESS, protocol, Permission.ALLOW, destinationTarget, portStart, portEnd);
                            firewallRules.add(rule);
                        }
                    }
                }
            }
        }

        return firewallRules;
    }

    @Override
    public @Nonnull Map<FirewallConstraints.Constraint, Object> getActiveConstraintsForFirewall(@Nonnull String firewallId) throws CloudException, InternalException {
        Firewall firewall = getFirewall(firewallId);
        RuleTarget sourceTarget = firewall.getRules().iterator().next().getSourceEndpoint();//GCE firewalls always have rules and they all have the same source so this makes sense

        HashMap<FirewallConstraints.Constraint, Object> constraints = new HashMap<FirewallConstraints.Constraint, Object>();
        constraints.put(FirewallConstraints.Constraint.PERMISSION, Permission.ALLOW);
        constraints.put(FirewallConstraints.Constraint.DIRECTION, Direction.INGRESS);
        constraints.put(FirewallConstraints.Constraint.SOURCE, sourceTarget);

        return constraints;
    }


    @Override
    @Deprecated
    public @Nonnull String getProviderTermForFirewall(@Nonnull Locale locale) {
        return "firewall";
    }
}
