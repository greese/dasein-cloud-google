package org.dasein.cloud.google.network;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Address;
import com.google.api.services.compute.model.Operation;
import com.google.common.util.concurrent.Futures;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.AbstractOperation;
import org.dasein.cloud.google.CloudUpdateOperation;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.capabilities.GCEIPAddressCapabilities;
import org.dasein.cloud.google.common.BusinessCloudException;
import org.dasein.cloud.google.util.model.GoogleNetworks;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * User: mgulimonov
 * Date: 12.04.2014
 */
public class GoogleIpAddressSupport implements IpAddressSupport {
    private Google cloud;

    public GoogleIpAddressSupport(Google cloud) {
        this.cloud = cloud;
    }

    @Override
    public void assign(@Nonnull String addressId, @Nonnull final String serverId) throws InternalException, CloudException {

        IpAddress ipAddress = getIpAddress(addressId);

        final VirtualMachine vm = cloud.getComputeServices().getVirtualMachineSupport().getVirtualMachine(serverId);
        final AccessConfig accessConfig = new AccessConfig();
        accessConfig.setName("External NAT");
        accessConfig.setKind("compute#accessConfig");
        accessConfig.setType("ONE_TO_ONE_NAT");
        accessConfig.setNatIP(ipAddress.getRawAddress().getIpAddress());

        cloud.submit(new CloudUpdateOperation("IpAddress.assign") {
            @Override
            public Operation createOperation(Google google) throws IOException, CloudException {
                return google.getGoogleCompute().instances()
                        .addAccessConfig(google.getProject(), vm.getProviderDataCenterId(), serverId, "nic0", accessConfig)
                        .execute();
            }
        });
    }

    @Override
    public void assignToNetworkInterface(@Nonnull String addressId, @Nonnull String nicId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not support NICs");
    }

    @Nonnull
    @Override
    public String forward(@Nonnull String addressId, int publicPort, @Nonnull Protocol protocol, int privatePort, @Nonnull String onServerId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding rules are not supported by GCE");
    }

    @Nonnull
    @Override
    public GCEIPAddressCapabilities getCapabilities() throws CloudException, InternalException {
        return new GCEIPAddressCapabilities(cloud);
    }


    @Override
    public @Nullable IpAddress getIpAddress(@Nonnull String addressId) throws InternalException, CloudException {
        Address address = getGCEIpAddress(addressId);
        return GoogleNetworks.toIpAddress(address);
    }

    @Nonnull
    @Override
    @Deprecated
    public String getProviderTermForIpAddress(@Nonnull Locale locale) {
        try {
            return getCapabilities().getProviderTermForIpAddress(locale);
        } catch( CloudException | InternalException e ) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    @Deprecated
    public Requirement identifyVlanForVlanIPRequirement() throws CloudException, InternalException {
        return getCapabilities().identifyVlanForVlanIPRequirement();
    }

    @Override
    public boolean isAssigned(@Nonnull AddressType type) {
        return type.equals(AddressType.PUBLIC);
    }

    @Override
    public boolean isAssigned(@Nonnull IPVersion version) throws CloudException, InternalException {
        return getCapabilities().isAssigned(version);
    }

    @Override
    public boolean isAssignablePostLaunch(@Nonnull IPVersion version) throws CloudException, InternalException {
        return getCapabilities().isAssignablePostLaunch(version);
    }

    @Override
    public boolean isForwarding() {
        return false;
    }

    @Override
    public boolean isForwarding(IPVersion version) throws CloudException, InternalException {
        return getCapabilities().isForwarding(version);
    }

    @Override
    public boolean isRequestable(@Nonnull AddressType type) {
        return type.equals(AddressType.PUBLIC);
    }

    @Override
    public boolean isRequestable(@Nonnull IPVersion version) throws CloudException, InternalException {
        return getCapabilities().isRequestable(version);
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return false;
    }

    @Nonnull
    @Override
    public Iterable<IpAddress> listPrivateIpPool(boolean unassignedOnly) throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Iterable<IpAddress> listPublicIpPool(boolean unassignedOnly) throws InternalException, CloudException {
        return listIpPool(IPVersion.IPV4, unassignedOnly);
    }

    @Override
    public @Nonnull Iterable<IpAddress> listIpPool(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        List<Address> googleAddresses = getGoogleAddresses(cloud.getProject(), cloud.getRegionId());
        return GoogleNetworks.toAddressList(googleAddresses);
    }

    @Nonnull
    @Override
    public Future<Iterable<IpAddress>> listIpPoolConcurrently(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        return Futures.immediateCheckedFuture(listIpPool(version, unassignedOnly));
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listIpPoolStatus(@Nonnull IPVersion version) throws InternalException, CloudException {
        List<Address> googleAddresses = getGoogleAddresses(cloud.getProject(), cloud.getRegionId());
        return GoogleNetworks.toResourceStatusIterable(googleAddresses);
    }

    @Nonnull
    @Override
    public Iterable<IpForwardingRule> listRules(@Nonnull String addressId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding rules are not supported by GCE");
    }

    @Nonnull
    @Override
    public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return getCapabilities().listSupportedIPVersions();
    }

    @Override
    public void releaseFromPool(@Nonnull String addressId) throws InternalException, CloudException {
        releaseGCEIPFromPool(addressId);
    }

    @Override
    public void releaseFromServer(@Nonnull String addressId) throws InternalException, CloudException {
        IpAddress address = getIpAddress(addressId);
        releaseGCEAddressFromServer(address);
    }

    @Nonnull
    @Override
    public String request(@Nonnull AddressType typeOfAddress) throws InternalException, CloudException {
        return null;
    }

    @Nonnull
    @Override
    public String request(@Nonnull IPVersion version) throws InternalException, CloudException {
        Address address = new Address();
        address.setName("a" + UUID.randomUUID().toString());

        requestGCEAddress(address);

        return getGCEIpAddress(address.getName()).getAddress();
    }

    @Nonnull
    @Override
    public String requestForVLAN(@Nonnull IPVersion version) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not support manual creation of IP Addresses for VLANs");
    }

    @Nonnull
    @Override
    public String requestForVLAN(@Nonnull IPVersion version, @Nonnull String vlanId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("GCE does not support manual creation of IP Addresses for VLANs");
    }

    @Override
    public void stopForward(@Nonnull String ruleId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding rules are not supported by GCE");
    }

    @Override
    public boolean supportsVLANAddresses(@Nonnull IPVersion ofVersion) throws InternalException, CloudException {
        return getCapabilities().supportsVLANAddresses(ofVersion);
    }

    @Nonnull
    @Override
    public String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    protected void requestGCEAddress(final Address address) throws CloudException, InternalException {

        cloud.submit(new CloudUpdateOperation("IpAddress.request") {
            @Override
            public Operation createOperation(Google google) throws IOException, CloudException {
                return cloud.getGoogleCompute().addresses().insert(cloud.getProject(), cloud.getRegionId(), address).execute();
            }
        });
    }

    protected List<Address> getGoogleAddresses(final String project, final String regionId) throws CloudException, InternalException {

        return cloud.fetch(new AbstractOperation<List<Address>>("IpAddress.getIpAddresses") {
            @Override
            public List<Address> createOperation(Google google) throws IOException, CloudException {
                Compute.Addresses.List list = cloud.getGoogleCompute().addresses().list(project, regionId);
                return list.execute().getItems();
            }
        });

    }

    protected Address getGCEIpAddress(final String addressId) throws CloudException, InternalException {

        return cloud.fetch(new AbstractOperation<Address>("IpAddress.getIpAddress") {
            @Override
            public Address createOperation(Google google) throws IOException, CloudException {
                Compute.Addresses.Get get = cloud.getGoogleCompute().addresses().get(cloud.getProject(), cloud.getRegionId(), addressId);
                return get.execute();
            }
        });

    }

    protected void releaseGCEIPFromPool(final String addressId) throws CloudException, InternalException {

        cloud.submit(new CloudUpdateOperation("IpAddress.releaseFromPool") {
            @Override
            public Operation createOperation(Google google) throws IOException, CloudException {
                return google.getGoogleCompute().addresses().delete(cloud.getProject(), cloud.getRegionId(), addressId).execute();
            }
        });

    }

    protected void releaseGCEAddressFromServer(IpAddress address) throws CloudException, InternalException {
        final String instance = address.getServerId();

        if (instance == null) {
            throw new BusinessCloudException("It's seems that IP " + address.getAddress() + " is not attached to any instance");
        }

        cloud.submit(new CloudUpdateOperation("IpAddress.releaseFromServer") {
            @Override
            public Operation createOperation(Google google) throws IOException, CloudException {
                final VirtualMachine vm = cloud.getComputeServices().getVirtualMachineSupport().getVirtualMachine(instance);
                final String zone = vm.getProviderDataCenterId();

                return google.getGoogleCompute().instances()
                        .deleteAccessConfig(cloud.getProject(), zone, instance, "External NAT", "nic0")
                        .execute();
            }
        });
    }

}
