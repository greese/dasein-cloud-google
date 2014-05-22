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

package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.*;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

public class GCEFirewallCapabilities extends AbstractCapabilities<Google> implements FirewallCapabilities{
    public GCEFirewallCapabilities(@Nonnull Google cloud){super(cloud);}

    @Override
    public @Nonnull FirewallConstraints getFirewallConstraintsForCloud() throws InternalException, CloudException{
        FirewallConstraints constraints = FirewallConstraints.getInstance();
        constraints.withConstraint(FirewallConstraints.Constraint.PERMISSION, FirewallConstraints.Level.REQUIRED);
        constraints.withConstraint(FirewallConstraints.Constraint.DIRECTION, FirewallConstraints.Level.REQUIRED);
        constraints.withConstraint(FirewallConstraints.Constraint.SOURCE, FirewallConstraints.Level.IF_DEFINED);

        return constraints;
    }

    @Override
    public @Nonnull String getProviderTermForFirewall(@Nonnull Locale locale){
        return "firewall";
    }

    @Override
    public @Nonnull Requirement identifyPrecedenceRequirement(boolean inVlan) throws InternalException, CloudException{
        return Requirement.NONE;
    }

    @Override
    public boolean isZeroPrecedenceHighest() throws InternalException, CloudException{
        return false;
    }

    @Override
    public @Nonnull Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan) throws InternalException, CloudException{
        Collection<RuleTargetType> destinationTypes = new ArrayList<RuleTargetType>();
        destinationTypes.add(RuleTargetType.VM);
        destinationTypes.add(RuleTargetType.VLAN);
        return destinationTypes;
    }

    @Override
    public @Nonnull Iterable<Direction> listSupportedDirections(boolean inVlan) throws InternalException, CloudException{
        Collection<Direction> directions = new ArrayList<Direction>();
        directions.add(Direction.INGRESS);
        return directions;
    }

    @Override
    public @Nonnull Iterable<Permission> listSupportedPermissions(boolean inVlan) throws InternalException, CloudException{
        Collection<Permission> permissions = new ArrayList<Permission>();
        permissions.add(Permission.ALLOW);
        return permissions;
    }

    @Override
    public @Nonnull Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan) throws InternalException, CloudException{
        Collection<RuleTargetType> sourceTypes = new ArrayList<RuleTargetType>();
        sourceTypes.add(RuleTargetType.CIDR);
        sourceTypes.add(RuleTargetType.VM);
        return sourceTypes;
    }

    @Override
    public boolean requiresRulesOnCreation() throws CloudException, InternalException{
        return true;
    }

    @Override
    public Requirement requiresVLAN() throws CloudException, InternalException{
        return Requirement.REQUIRED;
    }

    @Override
    public boolean supportsRules(@Nonnull Direction direction, @Nonnull Permission permission, boolean inVlan) throws CloudException, InternalException{
        return (permission.equals(Permission.ALLOW) && direction.equals(Direction.INGRESS));
    }

    @Override
    public boolean supportsFirewallCreation(boolean inVlan) throws CloudException, InternalException{
        return false;
    }

    @Override
    public boolean supportsFirewallDeletion() throws CloudException, InternalException{
        return false;
    }
}
