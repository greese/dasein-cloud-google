/**
 * Copyright (C) 2012-2015 Dell, Inc
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
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.capabilities.GCEIPAddressCapabilities;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.AbstractIpAddressSupport;
import org.dasein.cloud.network.AddressType;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddress;
import org.dasein.cloud.network.IpForwardingRule;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.util.APITrace;


import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Address;
import com.google.api.services.compute.model.AddressAggregatedList;
import com.google.api.services.compute.model.AddressList;
import com.google.api.services.compute.model.Operation;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class IPAddressSupport extends AbstractIpAddressSupport<Google> {
    static private final ExecutorService threadPool = Executors.newFixedThreadPool(10);
    static private final Logger logger = Google.getLogger(IPAddressSupport.class);

    protected IPAddressSupport(Google provider) {
        super(provider);
    }

    @Override
    public void assign(@Nonnull String addressId, @Nonnull String serverId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "IpAddress.assign");

        try{
            Compute gce = getProvider().getGoogleCompute();
            IpAddress ipAddress = getIpAddress(addressId);

            VirtualMachine vm = getProvider().getComputeServices().getVirtualMachineSupport().getVirtualMachine(serverId);

            AccessConfig accessConfig = new AccessConfig();
            accessConfig.setName("External NAT");
            accessConfig.setKind("compute#accessConfig");
            accessConfig.setType("ONE_TO_ONE_NAT");
            accessConfig.setNatIP(ipAddress.getRawAddress().getIpAddress());
            try{
                GoogleMethod method = new GoogleMethod(getProvider());
                //need to try and delete the existing access config if an ephemeral one exists
                try{
                    Operation job = gce.instances().deleteAccessConfig(getContext().getAccountNumber(), vm.getProviderDataCenterId(), vm.getName(), "External NAT", "nic0").execute();
                    method.getOperationComplete(getContext(), job, GoogleOperationType.ZONE_OPERATION, "", vm.getProviderDataCenterId());
                }
                catch(Exception ex){/* Don't care if there's an exception here */}
                Operation job = gce.instances().addAccessConfig(getContext().getAccountNumber(), vm.getProviderDataCenterId(), serverId, "nic0", accessConfig).execute();

                if(!method.getOperationComplete(getContext(), job, GoogleOperationType.ZONE_OPERATION, "", vm.getProviderDataCenterId())){
                    throw new CloudException("An error occurred assigning the IP: " + addressId + ": Operation timed out");
                }
    	    } catch (Exception ex) {
	            logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
    				throw new CloudException("An error occurred assigning the IP: " + addressId + ": " + ex.getMessage());
    		}
        }
        finally {
            APITrace.end();
        }
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

    private transient volatile GCEIPAddressCapabilities capabilities;
    private ListIpPoolCallable task;
    @Override
    public @Nonnull GCEIPAddressCapabilities getCapabilities(){
        if(capabilities == null){
            capabilities = new GCEIPAddressCapabilities(getProvider());
        }
        return capabilities;
    }

    @Nullable
    @Override
    public IpAddress getIpAddress(@Nonnull String addressId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "IpAddress.getIpAddress");
        try{
            try{
                Compute gce = getProvider().getGoogleCompute();
                AddressAggregatedList addressList = gce.addresses().aggregatedList(getContext().getAccountNumber()).setFilter("name eq " + addressId).execute();
                if(addressList != null && addressList.getItems() != null && !addressList.getItems().isEmpty())        {
                    Iterator<String> regions = addressList.getItems().keySet().iterator();
                    while(regions.hasNext()){
                        String region = regions.next();
                        if(addressList.getItems() != null && addressList.getItems().get(region) != null && addressList.getItems().get(region).getAddresses() != null && !addressList.getItems().get(region).getAddresses().isEmpty()){
                            for(Address address : addressList.getItems().get(region).getAddresses()){
                                if(address.getName().equals(addressId))return toIpAddress(address);
                            }
                        }
                    }
                }
    	    } catch (IOException ex) {
	            logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred getting the IPAddress: " + ex.getMessage());
    		}
            throw new InternalException("Could not find IPAddress: " + addressId);
        }
        finally {
            APITrace.end();
        }
    }

    @Nullable
    public String getIpAddressIdFromIP(@Nonnull String ipAddress, @Nonnull String regionId)throws InternalException, CloudException{
        try{
            Compute gce = getProvider().getGoogleCompute();
            AddressList addressList = gce.addresses().list(getContext().getAccountNumber(), regionId).execute();
            if(addressList != null && addressList.getItems() != null && !addressList.getItems().isEmpty()){
                for(Address address : addressList.getItems()){
                    if(ipAddress.equals(address.getAddress()))return address.getName();
                }
            }
            throw new InternalException("An address could not be found matching " + ipAddress + " in " + regionId);
	    } catch (IOException ex) {
            logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException("An error occurred finding the specified IPAddress: " + ex.getMessage());
		}
    }

    @Override
    @Deprecated
    public @Nonnull String getProviderTermForIpAddress(@Nonnull Locale locale) {
        return "Address";
    }

    @Override
    @Deprecated
    public @Nonnull Requirement identifyVlanForVlanIPRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    @Deprecated
    public boolean isAssigned(@Nonnull AddressType type) {
        if(type.equals(AddressType.PUBLIC))return true;
        return false;
    }

    @Override
    @Deprecated
    public boolean isAssigned(@Nonnull IPVersion version) throws CloudException, InternalException {
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Override
    @Deprecated
    public boolean isAssignablePostLaunch(@Nonnull IPVersion version) throws CloudException, InternalException {
        return true;
    }

    @Override
    @Deprecated
    public boolean isForwarding() {
        return false;
    }

    @Override
    @Deprecated
    public boolean isForwarding(IPVersion version) throws CloudException, InternalException {
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Override
    @Deprecated
    public boolean isRequestable(@Nonnull AddressType type) {
        return true;
    }

    @Override
    @Deprecated
    public boolean isRequestable(@Nonnull IPVersion version) throws CloudException, InternalException {
        if(version.equals(IPVersion.IPV4))return true;
        return false;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
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

    @Nonnull
    @Override
    public Iterable<IpAddress> listIpPool(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "IpAddress.listIpPool");
        try{
            if( !version.equals(IPVersion.IPV4) ) {
                return Collections.emptyList();
            }
            List<IpAddress> addresses = new ArrayList<IpAddress>();
            try{
                Compute gce = getProvider().getGoogleCompute();
                AddressList addressList = gce.addresses().list(getContext().getAccountNumber(), getContext().getRegionId()).execute();
                if(addressList != null && addressList.getItems() != null && !addressList.getItems().isEmpty()){
                    for(Address address : addressList.getItems()){
                        IpAddress ipAddress = toIpAddress(address);
                        if(ipAddress != null)addresses.add(ipAddress);
                    }
                }
                return addresses;
    	    } catch (IOException ex) {
	            logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred listing IPs: " + ex.getMessage());
    		}
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listIpPoolStatus(@Nonnull IPVersion version) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "IpAddress.listIpPoolStatus");
        try{
            if( !version.equals(IPVersion.IPV4) ) {
                return Collections.emptyList();
            }
            List<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
            try{
                Compute gce = getProvider().getGoogleCompute();
                AddressAggregatedList addressList = gce.addresses().aggregatedList(getContext().getAccountNumber()).execute();
                Iterator<String> regions = addressList.getItems().keySet().iterator();
                while(regions.hasNext()){
                    String region = regions.next();

                    if ((null != addressList) &&
                        (null != addressList.getItems()) &&
                        (null != addressList.getItems().get(region)) &&
                        (null != addressList.getItems().get(region).getAddresses())) {
                        for(Address address : addressList.getItems().get(region).getAddresses()){
                            ResourceStatus status = toStatus(address);
                            if (status != null) {
                                statuses.add(status);
                            }
                        }
                    }
                }
                return statuses;
    	    } catch (IOException ex) {
	            logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred listing IPs: " + ex.getMessage());
    		}
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public Iterable<IpForwardingRule> listRules(@Nonnull String addressId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding rules are not supported by GCE");
    }

    @Override
    public void releaseFromPool(@Nonnull String addressId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "IpAddress.releaseFromPool");
        try{
            try{
                IpAddress ipAddress = getIpAddress(addressId);
                Compute gce = getProvider().getGoogleCompute();
                Operation job = gce.addresses().delete(getContext().getAccountNumber(), ipAddress.getRegionId(), addressId).execute();

                GoogleMethod method = new GoogleMethod(getProvider());
                if(!method.getOperationComplete(getContext(), job, GoogleOperationType.REGION_OPERATION, ipAddress.getRegionId(), "")){
                    throw new CloudException("An error occurred releasing address: " + addressId + ": Operation timed out");
                }
    	    } catch (IOException ex) {
	            logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred releasing address: " + addressId + ": " + ex.getMessage());
    		}
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void releaseFromServer(@Nonnull String addressId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "IpAddress.releaseFromServer");
        try{
            Compute gce = getProvider().getGoogleCompute();
            try{
                Address address = gce.addresses().get(getContext().getAccountNumber(), getContext().getRegionId(), addressId).execute();
                String zone = "";
                String instance = "";
                if (null != address.getUsers()) {
                    for(String vm : address.getUsers()){
                        Pattern p = Pattern.compile("/zones/(.*?)/instances");
                        Matcher m = p.matcher(vm);
                        if(m.find()){
                            zone = m.group();
                            instance = vm;
                            break;
                        }
                    }
                }
                zone = zone.replace("/zones/", "");
                zone = zone.replace("/instances", "");
                instance = instance.substring(instance.lastIndexOf("/") + 1);
                Operation job = gce.instances().deleteAccessConfig(getContext().getAccountNumber(), zone, instance, "External NAT", "nic0").execute();

                GoogleMethod method = new GoogleMethod(getProvider());
                if(!method.getOperationComplete(getContext(), job, GoogleOperationType.ZONE_OPERATION, "", zone)){
                    throw new CloudException("An error occurred releasing the address from the server: Operation timed out");
                }
    	    } catch (IOException ex) {
	            logger.error(ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
                    throw new CloudException("An error occurred releasing the address from the server: " + ex.getMessage());
    		} catch (Exception ex) {
    		    logger.error(ex.getMessage());
    		}
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public String request(@Nonnull AddressType typeOfAddress) throws InternalException, CloudException {
        if (typeOfAddress.equals(AddressType.PUBLIC)) {
            return request(IPVersion.IPV4);
        } else {
            throw new OperationNotSupportedException("GCE only supports creation of public IP Addresses");
        }
    }

    @Nonnull
    @Override
    public String request(@Nonnull IPVersion version) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "IpAddress.request");
        try{
            if(version.equals(IPVersion.IPV4)){
                Compute gce = getProvider().getGoogleCompute();

                try{
                    Address address = new Address();
                    address.setName("a" + UUID.randomUUID().toString());
                    Operation job = gce.addresses().insert(getContext().getAccountNumber(), getContext().getRegionId(), address).execute();

                    GoogleMethod method = new GoogleMethod(getProvider());
                    return method.getOperationTarget(getContext(), job, GoogleOperationType.REGION_OPERATION, getContext().getRegionId(), "", false);
        	    } catch (IOException ex) {
    	            logger.error(ex.getMessage());
        			if (ex.getClass() == GoogleJsonResponseException.class) {
        				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
        				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
        			} else
                        throw new CloudException("An error occurred requesting an IPAddress: " + ex.getMessage());
        		}
            }
            else {
                throw new OperationNotSupportedException("GCE currently only supports IPv4");
            }
        }
        finally {
            APITrace.end();
        }
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
        return false;
    }

    private IpAddress toIpAddress(Address address){
        IpAddress ipAddress = new IpAddress();

        ipAddress.setIpAddressId(address.getName());
        ipAddress.setAddress(address.getAddress());
        ipAddress.setRegionId(address.getRegion().substring(address.getRegion().lastIndexOf("/") + 1));
        ipAddress.setAddressType(AddressType.PUBLIC);
        ipAddress.setVersion(IPVersion.IPV4);
        ipAddress.setForVlan(false);
        if(address.getUsers() != null && address.getUsers().size() > 0){
            for(String user : address.getUsers()){
                user = user.substring(user.lastIndexOf("/") + 1);
                ipAddress.setServerId(user);
            }
        }

        return ipAddress;
    }

    private ResourceStatus toStatus(Address address){
        return new ResourceStatus(address.getName(), address.getStatus().equals("RESERVED") ? false : true);
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[]{};
    }

    @Override
    public Future<Iterable<IpAddress>> listIpPoolConcurrently(IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        task = new ListIpPoolCallable(version, unassignedOnly);
        return threadPool.submit(task);
    }

    public class ListIpPoolCallable implements Callable<Iterable<IpAddress>> {
        IPVersion version;
        boolean unassignedOnly;

        public ListIpPoolCallable( IPVersion version, boolean unassignedOnly ) {
          this.version = version;
          this.unassignedOnly = unassignedOnly;
        }

        public Iterable<IpAddress> call() throws CloudException, InternalException {
            if (!version.equals(IPVersion.IPV4)) {
                return Collections.emptyList();
            }
            ProviderContext ctx = getProvider().getContext();
            if (ctx == null) {
                throw new CloudException("No context was set for this request");
            }

            ArrayList<IpAddress> list = new ArrayList<IpAddress>();
            Compute gce = getProvider().getGoogleCompute();

            try {
                AddressList foo = gce.addresses().list(getProvider().getContext().getAccountNumber(), null).execute();
                for (Address item : foo.getItems()) {
                    IpAddress ipAddress = new IpAddress();
                    ipAddress.setAddress(item.getAddress());
                    ipAddress.setIpAddressId(item.getName()); // item.getId()
                    ipAddress.setVersion(IPVersion.IPV4);
                    ipAddress.setRegionId(item.getRegion());
                    list.add(ipAddress);
                }
            } catch ( IOException e ) {
                throw new OperationNotSupportedException("Problem obtaining results for listIpPoolConcurrently");
            }
            return list;
        }
    }
}

