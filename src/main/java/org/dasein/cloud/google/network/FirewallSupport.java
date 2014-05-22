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

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.capabilities.GCEFirewallCapabilities;
import org.dasein.cloud.network.*;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.util.APITrace;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * Implements the firewall services supported in the Google API.
 * @author Drew Lyall
 * @version 2014.03 initial version
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
        try{
            //TODO
            return null;
        }
        finally{
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
        if(capabilities == null){
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
        //TODO
        return null;
    }

    @Override
    public @Nonnull Collection<FirewallRule> getRules(@Nonnull String firewallId) throws InternalException, CloudException {
        //TODO
        return null;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public @Nonnull Collection<Firewall> list() throws InternalException, CloudException{
        //GCE has a defacto Firewall for every network so will simply map a fake firewall to networks.
        ProviderContext ctx = provider.getContext();
        if( ctx == null ) {
            throw new InternalException("No context was established");
        }

        ArrayList<Firewall> firewalls = new ArrayList<Firewall>();
        try{
            Compute gce = provider.getGoogleCompute();
            List<Network> networks = gce.networks().list(ctx.getAccountNumber()).execute().getItems();
            if(networks != null && networks.size() > 0){
                for(Network network : networks){
                    if(network != null){
                        List<com.google.api.services.compute.model.Firewall> rules = gce.firewalls().list(ctx.getAccountNumber()).setFilter("network eq " + network.getName()).execute().getItems();
                        Firewall firewall = toFirewall(network, rules);
                        if(firewall != null)firewalls.add(firewall);
                    }
                }
            }
        }
        catch(IOException ex){
            logger.error(ex.getMessage());
            throw new CloudException("An error occurred while listing Firewalls: " + ex.getMessage());
        }
        return firewalls;
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listFirewallStatus() throws InternalException, CloudException {
        //TODO
        return null;
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
        //TODO: Implement me
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        //TODO: Implement me
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        //TODO: Implement me
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        //TODO: Implement me
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, @Nonnull RuleTarget target, int beginPort, int endPort) throws CloudException, InternalException {
        //TODO: Implement me
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
        if(rules != null){
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
        for(com.google.api.services.compute.model.Firewall googleRule : rules){
            for(String sourceRange : googleRule.getSourceRanges()){
                //Right now GCE only supports IPv4
                if(InetAddressUtils.isIPv4Address(sourceRange)) sourceRange = sourceRange + "/32";
                RuleTarget sourceTarget = RuleTarget.getCIDR(sourceRange);
                String vLanId = googleRule.getNetwork().substring(googleRule.getNetwork().lastIndexOf("/") + 1);

                for(com.google.api.services.compute.model.Firewall.Allowed allowed : googleRule.getAllowed()){
                    Protocol protocol = Protocol.valueOf(allowed.getIPProtocol());
                    RuleTarget destinationTarget;
                    int portStart;
                    int portEnd;

                    for(String portString : allowed.getPorts()){
                        if(portString.indexOf("-") > 0){
                            String[] parts = portString.split("-");
                            portStart = Integer.valueOf(parts[0]);
                            portEnd = Integer.valueOf(parts[1]);
                        }
                        else portStart = portEnd = Integer.valueOf(portString);

                        if(googleRule.getTargetTags() != null){
                            for(String targetTag : googleRule.getTargetTags()){
                                destinationTarget = RuleTarget.getVirtualMachine(targetTag);
                                FirewallRule rule = FirewallRule.getInstance(googleRule.getName(), "fw-" + vLanId, sourceTarget, Direction.INGRESS, protocol, Permission.ALLOW, destinationTarget, portStart, portEnd);
                                firewallRules.add(rule);
                            }
                        }
                        else{
                            destinationTarget = RuleTarget.getVlan(vLanId);
                            FirewallRule rule = FirewallRule.getInstance(googleRule.getName(), "fw-" + vLanId, sourceTarget, Direction.INGRESS, protocol, Permission.ALLOW, destinationTarget, portStart, portEnd);
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
