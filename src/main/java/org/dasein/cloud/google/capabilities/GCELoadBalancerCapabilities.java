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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.LbAlgorithm;
import org.dasein.cloud.network.LbEndpointType;
import org.dasein.cloud.network.LbPersistence;
import org.dasein.cloud.network.LbProtocol;
import org.dasein.cloud.network.LoadBalancerAddressType;
import org.dasein.cloud.network.LoadBalancerCapabilities;
import org.dasein.cloud.google.Google;

public class GCELoadBalancerCapabilities extends AbstractCapabilities<Google> implements LoadBalancerCapabilities {
	static private final Logger logger = Logger.getLogger(GCELoadBalancerCapabilities.class);

    public GCELoadBalancerCapabilities(@Nonnull Google Google) {
        super(Google);
    }
	
    @Nonnull
	@Override
	public LoadBalancerAddressType getAddressType() throws CloudException, InternalException {
    	return LoadBalancerAddressType.DNS;
	}

	@Override
	public int getMaxPublicPorts() throws CloudException, InternalException {
		// 0 means all ports
		return 0;
	}

	@Nonnull
	@Override
	public String getProviderTermForLoadBalancer(Locale locale) {
		return "load balancer"; // target pools are a component utilized by load balancer
	}

    @Nullable
    @Override
    public VisibleScope getLoadBalancerVisibleScope() {
        return VisibleScope.ACCOUNT_REGION;
    }

    @Override
	public boolean healthCheckRequiresLoadBalancer() throws CloudException, InternalException {
		/*
		 * http://salt.readthedocs.org/en/latest/topics/cloud/gce.html#http-health-check
		 * 
		 * It looks like Health check is user implemented, documents seem to always reference
		 * health check in a load balance context.
		 */
		return true;
	}

	@Override
	public Requirement identifyEndpointsOnCreateRequirement() throws CloudException, InternalException {
		/*
		 * https://developers.google.com/compute/docs/load-balancing/#configurelb
		 * 
		 * standing up load balancing appears to be a 3 step process 
		 * with the final step being to define listeners/endpoints
		 */
		return Requirement.OPTIONAL;
	}

	@Nonnull
	@Override
	public Requirement identifyListenersOnCreateRequirement() throws CloudException, InternalException {
		/*
		 * https://developers.google.com/compute/docs/load-balancing/#configurelb
		 * 
		 * standing up load balancing appears to be a 3 step process 
		 * with the final step being to define listeners/endpoints
		 */
		return Requirement.OPTIONAL;
	}

	@Override
	public boolean isAddressAssignedByProvider() throws CloudException, InternalException {
		/*
		 * https://developers.google.com/compute/docs/load-balancing/
		 * me@local:~$ gcutil --project=<project-id> addinstance www1 www2 www3 --zone=$ZONE \
         *          --tags=$TAG --metadata_from_file=startup-script:$HOME/lb_startup.sh
		 */
		return true;
	}

	@Override
	public boolean isDataCenterLimited() throws CloudException, InternalException {
		/* 
		 * https://developers.google.com/compute/docs/load-balancing/#algorithm
		 * 
		 * You can associate target pools in different zones with a single forwarding 
		 * rule to provide protection from planned or unplanned zone outages.
		 */
		return false;
	}

	@Nonnull
	@Override
	public Iterable<LbAlgorithm> listSupportedAlgorithms() throws CloudException, InternalException {
		/*
		 * https://developers.google.com/compute/docs/load-balancing/#algorithm
		 * By default, to distribute traffic to instances, Google Compute Engine picks an instance 
		 * based on a hash of the source IP and port and the destination IP and port. Incoming TCP 
		 * connections are spread across instances and each new connection may go to a different 
		 * instance. All packets for a connection are directed to the same instance until the 
		 * connection is closed.
		 * 
		 * https://developers.google.com/compute/docs/load-balancing/#sessionAffinity
		 * sessionAffinity
		 * NONE 5-tuple hashing provides a good distribution of traffic across many virtual machines but if you wanted to use a single backend with a specific client ('stick' a client to a single virtual machine instance), you can also specify the following options:
		 * CLIENT_IP_PROTO 3-tuple hashing, which includes the source / destination IP and network protocol
		 * CLIENT_IP 2-tuple hashing, which includes the source / destination IP
		 */
		return Collections.singletonList(LbAlgorithm.SOURCE);
	}

	@Nonnull
	@Override
	public Iterable<LbEndpointType> listSupportedEndpointTypes() throws CloudException, InternalException {
		return Collections.singletonList(LbEndpointType.VM);
	}
	
    static private volatile List<IPVersion> versions;
    
	@Nonnull
	@Override
	public Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
		// https://developers.google.com/compute/docs/networking
		/*
		 *  Google Compute Engine currently does not support IPv6
		 */
        if( versions == null ) {
            versions = Collections.unmodifiableList(Arrays.asList(
                    IPVersion.IPV4
            ));
        }
        return versions;
	}

	@Nonnull
	@Override
	public Iterable<LbPersistence> listSupportedPersistenceOptions() throws CloudException, InternalException {
		/*
		 * https://developers.google.com/compute/docs/load-balancing/#targetpools
		 * 
		 * sessionAffinity
		 * [Optional] Describes the method used to select a backend virtual machine instance. You can only set 
		 * this value during the creation of the target pool. Once set, you cannot modify this value. By default, 
		 * a 5-tuple method is used and the default value for this field is NONE. The 5-tuple hash method 
		 * selects a backend based on:
		 * Layer 4 Protocol (e.g. TCP, UDP)
		 * Source / Destination IP
		 * Source / Destination Port
		 * 5-tuple hashing provides a good distribution of traffic across many virtual machines but if 
		 * you wanted to use a single backend with a specific client ('stick' a client to a single virtual 
		 * machine instance), you can also specify the following options:
		 * CLIENT_IP_PROTO
		 * 3-tuple hashing, which includes the source / destination IP and network protocol
		 * CLIENT_IP
		 * 2-tuple hashing, which includes the source / destination IP
		 * In general, if you select a 3-tuple or 2-tuple method, it will provide for better session affinity 
		 * than the default 5-tuple method, at the cost of possibly unequal distribution of traffic.
		 * Caution: If a large portion of your clients are behind a proxy server, you should not use the 
		 * sessionAffinity feature because it would force all clients behind the proxy to be pinned to a 
		 * specific backend.
		 */
		return Collections.singletonList(LbPersistence.SUBNET);  // Best guess given above...
	}

	static private volatile List<LbProtocol> protocols;
	
	@Nonnull
	@Override
	public Iterable<LbProtocol> listSupportedProtocols() throws CloudException, InternalException {
		/*
		 * The type of protocol that this forwarding rule matches. Valid values are:
		 * "AH": Specifies the IP Authentication Header protocol.
		 * "ESP": Specifies the IP Encapsulating Security Payload protocol.
		 * "SCTP": Specifies the Stream Control Transmission Protocol.
		 * "TCP": Specifies the Transmission Control Protocol.
		 * "UDP": Specifies the User Datagram Protocol.
		 * If left empty, this field will default to TCP. Also note that certain protocols can only 
		 * be used with target pools or target instances:
		 * If you use ESP, AH, or SCTP, you must specify a target instance. It is not possible to specify 
		 * a target pool when using these protocols.
		 * If you use TCP or UDP, you can specify either a target pool or a target instance.
		 */
        if( protocols == null ) {
            protocols = Collections.unmodifiableList(Arrays.asList(
                    LbProtocol.RAW_TCP //,
                    //LbProtocol.SCTP,
                    //LbProtocol.UDP,
                    //LbProtocol.ESP,
                    //LbProtocol.AH
            ));
        }
        return protocols;
	}

	@Override
	public boolean supportsAddingEndpoints() throws CloudException, InternalException {
		// https://developers.google.com/compute/docs/load-balancing/
		/* 
		 * "You can add or remove instances from an existing target pool"
		 * 
		 * Caution: If your target pool currently has the sessionAffinity field set, 
		 * resizing the target pool could cause requests that come from the same IP 
		 * to go to a different instance initially. Eventually, all connections from 
		 * the IP will go to the same virtual machine, as the old connections close.
		 */
		return true;
	}

	@Override
	public boolean supportsMonitoring() throws CloudException, InternalException {
		// https://developers.google.com/compute/docs/load-balancing/
		/*
		 * Google Compute Engine load balancing allows you to create forwarding 
		 * rule objects that match and direct certain types of traffic to a load 
		 * balancer. A target pool object controls the load balancer and contains 
		 * the set of instances to send traffic to. Target pools also contain a health 
		 * check object to determine the health of instances in the corresponding 
		 * target pool.
		 */
		return true;
	}

	@Override
	public boolean supportsMultipleTrafficTypes() throws CloudException, InternalException {
		// https://developers.google.com/compute/docs/networking
		/*
		 *  Google Compute Engine currently does not support IPv6
		 */
		return false;
	}

    @Override
    public Requirement healthCheckRequiresName() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public Requirement identifyVlanOnCreateRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Requirement identifyHealthCheckOnCreateRequirement() throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

}
