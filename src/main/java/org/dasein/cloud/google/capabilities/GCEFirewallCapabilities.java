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

package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class GCEFirewallCapabilities extends AbstractCapabilities<Google> implements FirewallCapabilities {
    public GCEFirewallCapabilities( @Nonnull Google cloud ) {
        super(cloud);
    }

    @Override
    public @Nonnull FirewallConstraints getFirewallConstraintsForCloud() throws InternalException, CloudException {
        return FirewallConstraints.getInstance()
                .withConstraint(FirewallConstraints.Constraint.PERMISSION, FirewallConstraints.Level.REQUIRED)
                .withConstraint(FirewallConstraints.Constraint.DIRECTION, FirewallConstraints.Level.REQUIRED)
                .withConstraint(FirewallConstraints.Constraint.SOURCE, FirewallConstraints.Level.IF_DEFINED);
    }

    @Override
    public @Nonnull String getProviderTermForFirewall( @Nonnull Locale locale ) {
        return "firewall";
    }

    @Override
    public @Nullable VisibleScope getFirewallVisibleScope() {
        return VisibleScope.ACCOUNT_GLOBAL;
    }

    @Override
    public @Nonnull Requirement identifyPrecedenceRequirement( boolean inVlan ) throws InternalException, CloudException {
        return Requirement.NONE;
    }

    @Override
    public boolean isZeroPrecedenceHighest() throws InternalException, CloudException {
        return false;
    }

    private static volatile Iterable<RuleTargetType> allDestinationTypes;

    @Override
    public @Nonnull Iterable<RuleTargetType> listSupportedDestinationTypes( boolean inVlan ) throws InternalException, CloudException {
        if( allDestinationTypes == null ) {
            allDestinationTypes = Collections.unmodifiableList(Arrays.asList(RuleTargetType.VM, RuleTargetType.VLAN));
        }
        return allDestinationTypes;
    }

    @Override
    public @Nonnull Iterable<Direction> listSupportedDirections( boolean inVlan ) throws InternalException, CloudException {
        return Collections.unmodifiableList(Collections.singletonList(Direction.INGRESS));
    }

    @Override
    public @Nonnull Iterable<Permission> listSupportedPermissions( boolean inVlan ) throws InternalException, CloudException {
        return Collections.unmodifiableList(Collections.singletonList(Permission.ALLOW));
    }

    private static volatile Iterable<RuleTargetType> allSourceTypes;

    @Override
    public @Nonnull Iterable<RuleTargetType> listSupportedSourceTypes( boolean inVlan ) throws InternalException, CloudException {
        if( allSourceTypes == null ) {
            allSourceTypes = Collections.unmodifiableList(Arrays.asList(RuleTargetType.VM, RuleTargetType.CIDR));
        }
        return allSourceTypes;
    }

    @Override
    public boolean requiresRulesOnCreation() throws CloudException, InternalException {
        return true;
    }

    @Override
    public Requirement requiresVLAN() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public boolean supportsRules( @Nonnull Direction direction, @Nonnull Permission permission, boolean inVlan ) throws CloudException, InternalException {
        return ( permission.equals(Permission.ALLOW) && direction.equals(Direction.INGRESS) );
    }

    @Override
    public boolean supportsFirewallCreation( boolean inVlan ) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsFirewallDeletion() throws CloudException, InternalException {
        return false;
    }

    private static volatile Iterable<Protocol> allProtocolTypes;

    @Override
    public Iterable<Protocol> listSupportedProtocols( boolean inVlan ) throws InternalException, CloudException {
        if( allProtocolTypes == null ) {
            allProtocolTypes = Collections.unmodifiableList(Arrays.asList(Protocol.UDP, Protocol.TCP, Protocol.ICMP));
        }
        return allProtocolTypes;
    }

    @Override
    public Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan, Direction direction) throws InternalException, CloudException {
        if (Direction.INGRESS == direction) {
            return Collections.unmodifiableList(Arrays.asList(RuleTargetType.CIDR, RuleTargetType.VM, RuleTargetType.GLOBAL));
        } else if (Direction.EGRESS == direction) {
            return Collections.unmodifiableList(Arrays.asList(RuleTargetType.CIDR, RuleTargetType.VM, RuleTargetType.GLOBAL));
        } else {
            return null;
        }
    }

    @Override
    public Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan, Direction direction) throws InternalException, CloudException {
        if (Direction.INGRESS == direction) {
            return Collections.unmodifiableList(Arrays.asList(RuleTargetType.CIDR, RuleTargetType.VM, RuleTargetType.GLOBAL));
        } else if (Direction.EGRESS == direction) {
            return Collections.unmodifiableList(Arrays.asList(RuleTargetType.CIDR, RuleTargetType.VM, RuleTargetType.GLOBAL));
        } else {
            return null;
        }
    }
}
