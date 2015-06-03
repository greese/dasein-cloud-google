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

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.network.Direction;
import org.dasein.cloud.network.FirewallCapabilities;
import org.dasein.cloud.network.FirewallConstraints;
import org.dasein.cloud.network.Permission;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.network.RuleTargetType;
import org.dasein.cloud.util.NamingConstraints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class GCEFirewallCapabilities extends AbstractCapabilities<Google> implements FirewallCapabilities {

    public GCEFirewallCapabilities(Google provider) {
        super(provider);
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

    @Override
    @Deprecated
    public @Nonnull Iterable<RuleTargetType> listSupportedDestinationTypes( boolean inVlan ) throws InternalException, CloudException {
        return listSupportedDestinationTypes(inVlan, Direction.INGRESS);
    }

    @Override
    public @Nonnull Iterable<Direction> listSupportedDirections( boolean inVlan ) throws InternalException, CloudException {
        return Collections.unmodifiableList(Collections.singletonList(Direction.INGRESS));
    }

    @Override
    public @Nonnull Iterable<Permission> listSupportedPermissions( boolean inVlan ) throws InternalException, CloudException {
        return Collections.unmodifiableList(Collections.singletonList(Permission.ALLOW));
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<RuleTargetType> listSupportedSourceTypes( boolean inVlan ) throws InternalException, CloudException {
        return listSupportedSourceTypes(inVlan, Direction.INGRESS);
    }

    @Override
    public boolean requiresRulesOnCreation() throws CloudException, InternalException {
        return true;
    }

    @Override
    @Nonnull
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
    @Nonnull
    public Iterable<Protocol> listSupportedProtocols( boolean inVlan ) throws InternalException, CloudException {
        if( allProtocolTypes == null ) {
            allProtocolTypes = Collections.unmodifiableList(Arrays.asList(Protocol.UDP, Protocol.TCP, Protocol.ICMP));
        }
        return allProtocolTypes;
    }

    @Override
    @Nonnull
    public Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan, @Nonnull Direction direction) throws InternalException, CloudException {
        if (Direction.INGRESS == direction) {
            return Collections.unmodifiableList(Arrays.asList(RuleTargetType.CIDR, RuleTargetType.VM, RuleTargetType.GLOBAL));
        }
        else {
            return Collections.emptyList();
        }
    }

    @Override
    @Nonnull
    public Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan, @Nonnull Direction direction) throws InternalException, CloudException {
        if (Direction.INGRESS == direction) {
            return Collections.unmodifiableList(Arrays.asList(RuleTargetType.CIDR, RuleTargetType.VM, RuleTargetType.GLOBAL));
        }
        else {
            return Collections.emptyList();
        }
    }

    @Override
    public NamingConstraints getFirewallNamingConstraints() {
        return NamingConstraints.getAlphaNumeric(1, 63)
                .withRegularExpression("^[a-z][-a-z0-9]{0,61}[a-z0-9]$")
                .lowerCaseOnly()
                .withNoSpaces()
                .withLastCharacterSymbolAllowed(false)
                .constrainedBy('-');
    }
}
