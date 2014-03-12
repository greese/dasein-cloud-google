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

    @Nonnull @Override public FirewallConstraints getFirewallConstraintsForCloud() throws InternalException, CloudException{
        FirewallConstraints constraints = FirewallConstraints.getInstance();
        constraints.withConstraint(FirewallConstraints.Constraint.PERMISSION, FirewallConstraints.Level.REQUIRED);
        constraints.withConstraint(FirewallConstraints.Constraint.DIRECTION, FirewallConstraints.Level.REQUIRED);
        constraints.withConstraint(FirewallConstraints.Constraint.SOURCE, FirewallConstraints.Level.IF_DEFINED);

        return constraints;
    }

    @Nonnull @Override public String getProviderTermForFirewall(@Nonnull Locale locale){
        return "firewall";
    }

    @Nonnull @Override public Requirement identifyPrecedenceRequirement(boolean inVlan) throws InternalException, CloudException{
        return Requirement.NONE;
    }

    @Override public boolean isZeroPrecedenceHighest() throws InternalException, CloudException{
        return false;
    }

    @Nonnull @Override public Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan) throws InternalException, CloudException{
        Collection<RuleTargetType> destinationTypes = new ArrayList<RuleTargetType>();
        destinationTypes.add(RuleTargetType.VM);
        return destinationTypes;
    }

    @Nonnull @Override public Iterable<Direction> listSupportedDirections(boolean inVlan) throws InternalException, CloudException{
        Collection<Direction> directions = new ArrayList<Direction>();
        directions.add(Direction.INGRESS);
        return directions;
    }

    @Nonnull @Override public Iterable<Permission> listSupportedPermissions(boolean inVlan) throws InternalException, CloudException{
        Collection<Permission> permissions = new ArrayList<Permission>();
        permissions.add(Permission.ALLOW);
        return permissions;
    }

    @Nonnull @Override public Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan) throws InternalException, CloudException{
        Collection<RuleTargetType> sourceTypes = new ArrayList<RuleTargetType>();
        sourceTypes.add(RuleTargetType.CIDR);
        sourceTypes.add(RuleTargetType.VM);
        return sourceTypes;
    }

    @Override public boolean requiresRulesOnCreation() throws CloudException, InternalException{
        return true;
    }

    @Override public Requirement requiresVLAN() throws CloudException, InternalException{
        return Requirement.REQUIRED;
    }

    @Override public boolean supportsRules(@Nonnull Direction direction, @Nonnull Permission permission, boolean inVlan) throws CloudException, InternalException{
        return (permission.equals(Permission.ALLOW) && direction.equals(Direction.INGRESS));
    }

    @Override public boolean supportsFirewallCreation(boolean inVlan) throws CloudException, InternalException{
        return true;
    }

    @Override public boolean supportsFirewallDeletion() throws CloudException, InternalException{
        return true;
    }
}
