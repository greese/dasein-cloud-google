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

import java.io.IOException;
import java.util.*;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.google.api.services.compute.model.Firewall.Allowed;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;

import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEFirewallCapabilities;
import org.dasein.cloud.network.*;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.util.APITrace;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
/**
 * Implements the firewall services supported in the Google API.
 * @author Drew Lyall
 * @version 2014.03 initial version
 * @since 2014.03
 */
public class FirewallSupport extends AbstractFirewallSupport {
	static private final Logger logger = Google.getLogger(FirewallSupport.class);

	private Google provider = null;

	FirewallSupport(Google provider) {
		super(provider);
        this.provider = provider;
	}

	@Override
	public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull RuleTarget sourceEndpoint, @Nonnull Protocol protocol, @Nonnull RuleTarget destinationEndpoint, int beginPort, int endPort, int precedence) throws CloudException, InternalException {
        APITrace.begin(provider, "Firewall.authorize");
        try{
            if( Permission.DENY.equals(permission) ) {
                throw new OperationNotSupportedException("GCE does not support DENY rules");
            }
            if( direction.equals(Direction.EGRESS) ){
                throw new OperationNotSupportedException("GCE does not support EGRESS rules");
            }
            Firewall firewall = getFirewall(firewallId);

            Compute gce = provider.getGoogleCompute();
            Operation job = null;
            com.google.api.services.compute.model.Firewall googleFirewall = new com.google.api.services.compute.model.Firewall();

            googleFirewall.setName(firewall.getName());
            googleFirewall.setDescription(firewall.getDescription());

            VLAN vlan = provider.getNetworkServices().getVlanSupport().getVlan(firewall.getProviderVlanId());
            googleFirewall.setNetwork(vlan.getTag("contentLink"));

            ArrayList<Allowed> allowedRules = new ArrayList<Allowed>();
            ArrayList<String> allowedSourceIPs = null;
            ArrayList<String> allowedSourceVMs = null;
            ArrayList<String> allowedTargetVMs = null;
            //First we need to rebuild from existing rules
            Collection<FirewallRule> firewallRules = firewall.getRules();
            for(FirewallRule rule : firewallRules){
                Allowed allowed = new Allowed();
                if (rule.getProtocol() != Protocol.ICMP) {
                    ArrayList<String> allowedPorts = new ArrayList<String>();
                    if(rule.getEndPort() == 0 || rule.getStartPort() == rule.getEndPort()){
                        allowedPorts.add(rule.getStartPort() + "");
                    }
                    else {
                        allowedPorts.add(rule.getStartPort() + "-" + rule.getEndPort());
                    }
                    allowed.setPorts(allowedPorts);
                }
                allowed.setIPProtocol(rule.getProtocol().name());
                allowedRules.add(allowed);

                RuleTargetType sourceType = rule.getSourceEndpoint().getRuleTargetType();
                if(sourceType.equals(RuleTargetType.CIDR)){
                    if(allowedSourceIPs == null)allowedSourceIPs = new ArrayList<String>();
                    allowedSourceIPs.add(rule.getSourceEndpoint().getCidr());
                }
                else if(sourceType.equals(RuleTargetType.VM)){
                    if(allowedSourceVMs == null)allowedSourceVMs = new ArrayList<String>();
                    allowedSourceVMs.add(rule.getSourceEndpoint().getProviderVirtualMachineId());
                }
                else throw new CloudException("GCE only supports CIDRs or VMs as valid sources.");

                RuleTargetType destinationType = rule.getDestinationEndpoint().getRuleTargetType();
                if(destinationType.equals(RuleTargetType.VM)){
                    if(allowedTargetVMs == null)allowedTargetVMs = new ArrayList<String>();
                    allowedTargetVMs.add(rule.getDestinationEndpoint().getProviderVirtualMachineId());
                }
                else if(!destinationType.equals(RuleTargetType.VLAN)) throw new CloudException("GCE only supports VMs or VLans as valid targets.");
            }

            //Then regardless of whether there were already some we add the new one
            RuleTargetType sourceType = sourceEndpoint.getRuleTargetType();
            if(sourceType.equals(RuleTargetType.CIDR)){
                if(allowedSourceIPs == null)allowedSourceIPs = new ArrayList<String>();
                allowedSourceIPs.add(sourceEndpoint.getCidr());
            }
            else if(sourceType.equals(RuleTargetType.VM)){
                if(allowedSourceVMs == null)allowedSourceVMs = new ArrayList<String>();
                allowedSourceVMs.add(sourceEndpoint.getProviderVirtualMachineId());
            }
            else throw new CloudException("GCE only supports CIDRs or VMs as valid sources.");

            RuleTargetType destinationType = destinationEndpoint.getRuleTargetType();
            if(destinationType.equals(RuleTargetType.VM)){
                if(allowedTargetVMs == null)allowedTargetVMs = new ArrayList<String>();
                allowedTargetVMs.add(destinationEndpoint.getProviderVirtualMachineId());
            }
            else if(!destinationType.equals(RuleTargetType.VLAN)) throw new CloudException("GCE only supports VMs or VLans as valid targets.");

            Allowed allowed = new Allowed();
            String portString = "";
            if (protocol != Protocol.ICMP) {
                ArrayList<String> allowedPorts = new ArrayList<String>();
                if(endPort == 0 || beginPort == endPort){
                    portString = beginPort + "";
                } else {
                    portString = beginPort + "-" + endPort;
                }
                allowedPorts.add(portString);
                allowed.setPorts(allowedPorts);
            }
            allowed.setIPProtocol(protocol.name());
            allowedRules.add(allowed);

            if(allowedSourceIPs != null)googleFirewall.setSourceRanges(allowedSourceIPs);
            if(allowedSourceVMs != null)googleFirewall.setSourceTags(allowedSourceVMs);
            if(allowedTargetVMs != null)googleFirewall.setTargetTags(allowedTargetVMs);
            googleFirewall.setAllowed(allowedRules);

            try{
                job = gce.firewalls().update(provider.getContext().getAccountNumber(), firewallId, googleFirewall).execute();
                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "")){
                    throw new CloudException("An error occurred updating firewall " + firewallId + ": Operation Timed Out");
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred updating firewall " + firewallId + ": " + ex.getMessage());
            }

            return getFirewallRuleId(googleFirewall, sourceEndpoint, allowed, portString);
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public @Nonnull String create(@Nonnull FirewallCreateOptions options)throws InternalException, CloudException {
        APITrace.begin(provider, "Firewall.create");
        try{
            Compute gce = provider.getGoogleCompute();
            Operation job = null;
            com.google.api.services.compute.model.Firewall firewall = new com.google.api.services.compute.model.Firewall();
            String name = options.getName().replace(" ", "").replace("-", "").replace(":", "").toLowerCase();
            firewall.setName(name);
            firewall.setDescription(options.getDescription());

            VLAN vlan = provider.getNetworkServices().getVlanSupport().getVlan(options.getProviderVlanId());
            firewall.setNetwork(vlan.getTag("contentLink"));

            ArrayList<String> allowedSourceIPs = null;
            ArrayList<String> allowedSourceVMs = null;
            ArrayList<String> allowedTargetVMs = null;

            ArrayList<Allowed> allowedRules = new ArrayList<Allowed>();
            FirewallRuleCreateOptions[] rules = options.getInitialRules();
            if(rules != null && rules.length > 0){
                for(FirewallRuleCreateOptions current : rules){
                    Allowed allowed = new Allowed();
                    allowed.setIPProtocol(current.getProtocol().name());

                    // Allowed ports may only be specified on rules whose protocol is one of [TCP, UDP, SCTP]
                    if (current.getProtocol() != Protocol.ICMP) {
                        ArrayList<String> allowedPorts = new ArrayList<String>();
                        if(current.getPortRangeEnd() == 0 || current.getPortRangeStart() == current.getPortRangeEnd()){
                            allowedPorts.add(current.getPortRangeStart() + "");
                        }
                        else{
                            allowedPorts.add(current.getPortRangeStart() + "-" + current.getPortRangeEnd());
                        }

                        allowed.setPorts(allowedPorts);
                    }
                    allowedRules.add(allowed);

                    RuleTarget sourceTarget = current.getSourceEndpoint();
                    if(sourceTarget != null && sourceTarget.getRuleTargetType().equals(RuleTargetType.CIDR)){
                        if(allowedSourceIPs == null)allowedSourceIPs = new ArrayList<String>();
                        allowedSourceIPs.add(sourceTarget.getCidr());
                    }
                    else if(sourceTarget != null && sourceTarget.getRuleTargetType().equals(RuleTargetType.VM)){
                        if(allowedSourceVMs == null )allowedSourceVMs = new ArrayList<String>();
                        allowedSourceVMs.add(sourceTarget.getProviderVirtualMachineId());
                    }
                    else if (sourceTarget != null){
                        throw new CloudException("GCE does not support global or network sources for Firewall rules.");
                    }

                    RuleTarget destinationTarget = current.getDestinationEndpoint();
                    if(destinationTarget != null && destinationTarget.getRuleTargetType().equals(RuleTargetType.VM)){
                        if(allowedTargetVMs == null)allowedTargetVMs = new ArrayList<String>();
                        allowedTargetVMs.add(destinationTarget.getProviderVirtualMachineId());
                    }
                    else if(destinationTarget != null){
                        throw new CloudException("GCE only supports instances as valid rule targets.");
                    }
                }
                if(allowedSourceIPs != null)firewall.setSourceRanges(allowedSourceIPs);
                if(allowedSourceVMs != null)firewall.setSourceTags(allowedSourceVMs);
                if(allowedTargetVMs != null)firewall.setTargetTags(allowedTargetVMs);
                firewall.setAllowed(allowedRules);
            }
            else throw new CloudException("GCE Firewalls must be created with at least one rule");

            try{
                job = gce.firewalls().insert(provider.getContext().getAccountNumber(), firewall).execute();
                GoogleMethod method = new GoogleMethod(provider);
                return method.getOperationTarget(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "", false);
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred creating firewall " + options.getName() + ": " + ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
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
	public void delete(@Nonnull String firewallId) throws InternalException, CloudException {
        APITrace.begin(provider, "Firewall.delete");
        try{
            Operation job = null;
            try{
                Compute gce = provider.getGoogleCompute();
                job = gce.firewalls().delete(provider.getContext().getAccountNumber(), firewallId).execute();
                GoogleMethod method = new GoogleMethod(provider);
                if(!method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "")){
                    throw new CloudException("An error occurred while deleting firewall " + firewallId + ": Operation Timed Out.");
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
                throw new CloudException("An error occurred while deleting firewall " + firewallId + ": " + ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
	}

	@Override
	public Firewall getFirewall(@Nonnull String firewallId) throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();
		if( ctx == null ) {
			throw new CloudException("No context has been established for this request");
		}

        Compute gce = provider.getGoogleCompute();
        try{
            com.google.api.services.compute.model.Firewall firewall = gce.firewalls().get(ctx.getAccountNumber(), firewallId).execute();
            return toFirewall(firewall);
        }
        catch(IOException ex){
            logger.error("An error occurred while getting firewall " + firewallId + ": " + ex.getMessage());
            throw new CloudException(ex.getMessage());
        }
	}

    private @Nullable Firewall toFirewall(@Nonnull com.google.api.services.compute.model.Firewall googleFirewall)throws CloudException, InternalException{
        Firewall firewall = new Firewall();
        //firewall.setProviderFirewallId(googleFirewall.getId() + "");// - GCE uses name as ID
        firewall.setProviderFirewallId(googleFirewall.getName());
        firewall.setRegionId(provider.getContext().getRegionId());
        firewall.setAvailable(true);
        firewall.setActive(true);
        firewall.setName(googleFirewall.getName());
        firewall.setDescription(googleFirewall.getDescription());
        final String network = googleFirewall.getNetwork();
        if (network != null) {
            firewall.setProviderVlanId(network.substring(network.lastIndexOf("/") + 1));
        }
        firewall.setRules(firewallToRules(googleFirewall));

        if(googleFirewall.getTargetTags() != null && googleFirewall.getTargetTags().size() > 0){
            int count = 0;
            for(String targetVM : googleFirewall.getTargetTags()){
                firewall.setTag("destinationVM_" + count, targetVM);
            }
        }
        return firewall;
    }

	@Override
    @Deprecated
	public @Nonnull String getProviderTermForFirewall(@Nonnull Locale locale) {
		return "firewall";
	}

	@Override
	public @Nonnull Collection<FirewallRule> getRules(@Nonnull String firewallId) throws InternalException, CloudException {
        APITrace.begin(provider, "Firewall.getRules");
        try{
            ProviderContext ctx = provider.getContext();
            if( ctx == null ) {
                throw new CloudException("No context has been established for this request");
            }

            try{
                Compute gce = provider.getGoogleCompute();
                com.google.api.services.compute.model.Firewall firewall = gce.firewalls().get(ctx.getAccountNumber(), firewallId).execute();
                return firewallToRules(firewall);
            }
            catch(IOException ex){
                logger.error("An error occurred while getting firewall rules for: " + firewallId + ": " + ex.getMessage());
                throw new CloudException(ex.getMessage());
            }
        }
        finally{
            APITrace.end();
        }
	}

    private ArrayList<FirewallRule> firewallToRules(com.google.api.services.compute.model.Firewall firewall){
        ArrayList<FirewallRule> rules = new ArrayList<FirewallRule>();

        if(firewall.getSourceRanges() != null){
            for(String sourceRange : firewall.getSourceRanges()){
                RuleTarget sourceTarget = RuleTarget.getCIDR(sourceRange);

                if(firewall.getTargetTags() != null && firewall.getTargetTags().size() > 0){
                    for(String targetInstance : firewall.getTargetTags()){
                        RuleTarget destinationTarget = RuleTarget.getVirtualMachine(targetInstance);

                        for(Allowed allowed : firewall.getAllowed()){
                            List<String> ports = allowed.getPorts();
                            String portString = "";
                            int startPort = -1;
                            int endPort = -1;
                            if (ports != null) {
                                for(String portRange : ports){
                                    if(portRange.contains("-")){
                                        startPort = Integer.parseInt(portRange.split("-")[0]);
                                        endPort = Integer.parseInt(portRange.split("-")[1]);
                                    }
                                    else{
                                        startPort = Integer.parseInt(portRange);
                                        endPort = Integer.parseInt(portRange);
                                    }
                                    portString += portRange + "_";
                                }
                                portString = portString.substring(0, portString.length()-1);//To remove trailing underscore
                            }

                            final String firewallRuleId = getFirewallRuleId(firewall, sourceTarget, allowed, portString);
                            FirewallRule rule = FirewallRule.getInstance(firewallRuleId, firewall.getName(), sourceTarget, Direction.INGRESS, Protocol.valueOf(allowed.getIPProtocol().toUpperCase()), Permission.ALLOW, destinationTarget, startPort, endPort);
                            rules.add(rule);
                        }
                    }
                }
                else{
                    RuleTarget destinationTarget = RuleTarget.getVlan(firewall.getNetwork());

                    for(Allowed allowed : firewall.getAllowed()){
                        List<String> ports = allowed.getPorts();
                        String portString = "0-0";
                        int startPort = 0;
                        int endPort = 0;
                        if(ports != null){
                            for(String portRange : ports){
                                if(portRange.contains("-")){
                                    startPort = Integer.parseInt(portRange.split("-")[0]);
                                    endPort = Integer.parseInt(portRange.split("-")[1]);
                                }
                                else{
                                    startPort = Integer.parseInt(portRange);
                                    endPort = Integer.parseInt(portRange);
                                }
                                portString += portRange + "_";
                            }
                            portString = portString.substring(0, portString.length()-1);//To remove trailing underscore
                        }
                        final String firewallRuleId = getFirewallRuleId(firewall, sourceTarget, allowed, portString);
                        FirewallRule rule = FirewallRule.getInstance(firewallRuleId, firewall.getName(), sourceTarget, Direction.INGRESS, Protocol.valueOf(allowed.getIPProtocol().toUpperCase()), Permission.ALLOW, destinationTarget, startPort, endPort);
                        rules.add(rule);
                    }
                }
            }
        }
        if(firewall.getSourceTags() != null && firewall.getSourceTags().size() > 0){
            for(String sourceInstance: firewall.getSourceTags()){
                RuleTarget sourceTarget = RuleTarget.getVirtualMachine(sourceInstance);

                if(firewall.getTargetTags() != null && firewall.getTargetTags().size() > 0){
                    for(String targetInstance : firewall.getTargetTags()){
                        RuleTarget destinationTarget = RuleTarget.getVirtualMachine(targetInstance);

                        for(Allowed allowed : firewall.getAllowed()){
                            List<String> ports = allowed.getPorts();
                            String portString = "";
                            int startPort = -1;
                            int endPort = -1;
                            if (ports != null) {

                                for(String portRange : ports){
                                    if(portRange.contains("-")){
                                        startPort = Integer.parseInt(portRange.split("-")[0]);
                                        endPort = Integer.parseInt(portRange.split("-")[1]);
                                    }
                                    else{
                                        startPort = Integer.parseInt(portRange);
                                        endPort = Integer.parseInt(portRange);
                                    }
                                    portString += portRange + "_";
                                }
                                portString = portString.substring(0, portString.length()-1);//To remove trailing underscore
                            }

                            final String firewallRuleId = getFirewallRuleId(firewall, sourceTarget, allowed, portString);
                            FirewallRule rule = FirewallRule.getInstance(firewallRuleId, firewall.getName(), sourceTarget, Direction.INGRESS, Protocol.valueOf(allowed.getIPProtocol().toUpperCase()), Permission.ALLOW, destinationTarget, startPort, endPort);
                            rules.add(rule);
                        }
                    }
                }
                else{
                    RuleTarget destinationTarget = RuleTarget.getVlan(firewall.getNetwork());

                    for(Allowed allowed : firewall.getAllowed()){
                        List<String> ports = allowed.getPorts();
                        String portString = "";
                        int startPort = -1;
                        int endPort = -1;
                        if (ports != null) {
                            for(String portRange : ports){
                                if(portRange.contains("-")){
                                    startPort = Integer.parseInt(portRange.split("-")[0]);
                                    endPort = Integer.parseInt(portRange.split("-")[1]);
                                }
                                else{
                                    startPort = Integer.parseInt(portRange);
                                    endPort = Integer.parseInt(portRange);
                                }
                                portString += portRange + "_";
                            }
                            portString = portString.substring(0, portString.length()-1);//To remove trailing underscore
                        }

                        final String firewallRuleId = getFirewallRuleId(firewall, sourceTarget, allowed, portString);
                        FirewallRule rule = FirewallRule.getInstance(firewallRuleId, firewall.getName(), sourceTarget, Direction.INGRESS, Protocol.valueOf(allowed.getIPProtocol().toUpperCase()), Permission.ALLOW, destinationTarget, startPort, endPort);
                        rules.add(rule);
                    }
                }
            }
        }
        return rules;
    }

    private String getFirewallRuleId(com.google.api.services.compute.model.Firewall firewall, RuleTarget sourceTarget,
                                     Allowed allowed, String portString) {
        final String source = sourceTarget.getCidr() == null ? sourceTarget.getProviderVirtualMachineId() : sourceTarget.getCidr();
        return firewall.getName() + "-" + allowed.getIPProtocol() + "-" + portString + "-" + source;
    }

    @Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public @Nonnull Collection<Firewall> list() throws InternalException, CloudException {
		ProviderContext ctx = provider.getContext();

		if( ctx == null ) {
			throw new CloudException("No context has been established for this request");
		}

		ArrayList<Firewall> list = new ArrayList<Firewall>();
        try{
            Compute gce = provider.getGoogleCompute();
            List<com.google.api.services.compute.model.Firewall> firewalls = gce.firewalls().list(ctx.getAccountNumber()).execute().getItems();
            for(int i=0;i<firewalls.size();i++){
                Firewall firewall = toFirewall(firewalls.get(i));
                if(firewall != null)list.add(firewall);
            }
        }
        catch(IOException ex){
            logger.error(ex.getMessage());
            throw new CloudException("An error occurred while listing firewalls: " + ex.getMessage());
        }
        return list;
	}

	@Override
	public @Nonnull Iterable<ResourceStatus> listFirewallStatus() throws InternalException, CloudException {
        ArrayList<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
        Collection<Firewall> firewalls = list();
        for(Firewall firewall : firewalls){
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
}
