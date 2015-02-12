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

package org.dasein.cloud.google.compute.server;

import javax.annotation.Nonnull;

import com.google.api.services.compute.Compute;
import com.google.api.services.replicapool.Replicapool;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ci.AbstractConvergedInfrastructureSupport;
import org.dasein.cloud.ci.CIFilterOptions;
import org.dasein.cloud.ci.CIProvisionOptions;
import org.dasein.cloud.ci.CIServices;
import org.dasein.cloud.ci.ConvergedInfrastructure;
import org.dasein.cloud.ci.ConvergedInfrastructureSupport;
import org.dasein.cloud.ci.ReplicapoolTemplate;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.capabilities.GCEInstanceCapabilities;
import org.dasein.cloud.google.capabilities.GCEReplicapoolCapabilities;
import org.dasein.cloud.util.APITrace;
import org.apache.log4j.Logger;

/**
 * Implements the volume services supported in the Google API.
 * @author Roger Unwin
 * @version 2015.03 initial version
 * @since 2015.03
 */
public class ReplicapoolSupport extends AbstractConvergedInfrastructureSupport <Google> {
    static private final Logger logger = Google.getLogger(ReplicapoolSupport.class);
    private Google provider = null;

    public ReplicapoolSupport(Google provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.isSubscribed");
        try {
            return true;  // cop-out for now.
        } finally{
            APITrace.end();
        }
    }

    // template CRUD in here or in Template class? 

    public CIProvisionOptions createCITemplate(@Nonnull String topologyId) {
        ReplicapoolTemplate template = new ReplicapoolTemplate(topologyId, null, false, false, null, null, null, false, false);
        boolean success = template.create(provider);
        CIProvisionOptions foo = CIProvisionOptions.getInstance(topologyId);
        return foo;
    }

    public boolean deleteCITemplate(@Nonnull String topologyId) {
        return false;
        //ReplicapoolTemplate
    }
    
    
    private transient volatile GCEReplicapoolCapabilities capabilities;

    public @Nonnull GCEReplicapoolCapabilities getCapabilities() {
        if( capabilities == null ) {
            capabilities = new GCEReplicapoolCapabilities();
        }
        return capabilities;
    }

    @Override
    public Iterable<ConvergedInfrastructure> listConvergedInfrastructures(CIFilterOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.listConvergedInfrastructures");
        try {
             ReplicapoolSupport rp = provider.getGoogleReplicapool();


            // TODO Auto-generated method stub
            return null;
        } finally{
            APITrace.end();
        }
    }

    @Override
    public Iterable<String> listVirtualMachines(String inCIId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.listVirtualMachines");
        try {
            // TODO Auto-generated method stub
            return null;
        } finally{
            APITrace.end();
        }
    }

    @Override
    public Iterable<String> listVLANs(String inCIId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.listVLANs");
        try {
            // TODO Auto-generated method stub
            return null;
        } finally{
            APITrace.end();
        }
    }

    /*
     * Create a replicaPool based on options in CIProvisionOptions options
     * @see org.dasein.cloud.ci.ConvergedInfrastructureSupport#provision(org.dasein.cloud.ci.CIProvisionOptions)
     */
    @Override
    public ConvergedInfrastructure provision(CIProvisionOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.provision");
        try {
            // TODO Auto-generated method stub
            return null;
        } finally{
            APITrace.end();
        }
    }

    @Override
    public void terminate(String ciId, String explanation) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.provision");
        try {
            // TODO Auto-generated method stub
        } finally{
            APITrace.end();
        }
    }


}
