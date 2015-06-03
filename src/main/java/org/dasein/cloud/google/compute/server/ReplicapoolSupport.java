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

package org.dasein.cloud.google.compute.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEReplicapoolCapabilities;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ci.AbstractConvergedInfrastructureSupport;
import org.dasein.cloud.ci.CIFilterOptions;
import org.dasein.cloud.ci.CIProvisionOptions;
import org.dasein.cloud.ci.ConvergedInfrastructure;
import org.dasein.cloud.ci.ConvergedInfrastructureState;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.util.APITrace;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.replicapool.Replicapool;
import com.google.api.services.replicapool.model.InstanceGroupManager;
import com.google.api.services.replicapool.model.InstanceGroupManagerList;
import com.google.api.services.replicapool.model.Operation;

/**
 * Implements the replicapool services supported in the Google API.
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

    private transient volatile GCEReplicapoolCapabilities capabilities;

    public @Nonnull GCEReplicapoolCapabilities getCapabilities() {
        if( capabilities == null ) {
            capabilities = new GCEReplicapoolCapabilities(provider);
        }
        return capabilities;
    }

    @Override
    public Iterable<ConvergedInfrastructure> listConvergedInfrastructures(CIFilterOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.listConvergedInfrastructures");
        List<ConvergedInfrastructure> convergedInfrastrutures = new ArrayList<ConvergedInfrastructure>();
        try {
             Replicapool rp = provider.getGoogleReplicapool();

             InstanceGroupManagerList result = null;
             try {
                 for (Region region : provider.getDataCenterServices().listRegions()) {
                     String regionName = region.getProviderRegionId();
                     for (DataCenter dataCenter : provider.getDataCenterServices().listDataCenters(regionName)) {
                         String dataCenterId = dataCenter.getProviderDataCenterId();
                         result = rp.instanceGroupManagers().list(provider.getContext().getAccountNumber(), dataCenterId).execute(); //provider.getContext().getRegionId()
                         if (null != result.getItems()) {
                             for (InstanceGroupManager item : result.getItems()) {
                                 ConvergedInfrastructure ci = ConvergedInfrastructure.getInstance(provider.getContext().getAccountNumber(), 
                                         regionName, dataCenterId, item.getId().toString(), ConvergedInfrastructureState.RUNNING, item.getName(), item.getDescription(), item.getSelfLink());

                                 convergedInfrastrutures.add(ci);
                             }
                         }
                     }
                 }
             } catch ( IOException e ) {
                 e.printStackTrace();
             }
        } finally{
            APITrace.end();
        }
        return convergedInfrastrutures;
    }

    @Override
    public Iterable<String> listVirtualMachines(String inCIId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.listVirtualMachines");
        List<String> vms = new ArrayList<String>();
        try {
            Replicapool rp = provider.getGoogleReplicapool();
            Compute gce = provider.getGoogleCompute();

            InstanceGroupManager pool = rp.instanceGroupManagers().get(provider.getContext().getAccountNumber(), "us-central1-f", inCIId).execute();
            String baseInstanceName = pool.getBaseInstanceName();
            InstanceList result = gce.instances().list(provider.getContext().getAccountNumber(), "us-central1-f").execute();
            for (Instance instance : result.getItems()) {
                if (instance.getName().startsWith(baseInstanceName + "-")) {
                    vms.add(instance.getName());
                }
            }
            return vms;
        } catch ( IOException e ) {
            e.printStackTrace();
        } finally{
            APITrace.end();
        }
        return null;
    }

    @Override
    public Iterable<String> listVLANs(String inCIId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.listVLANs");
        List<String> nets = new ArrayList<String>();
        try {
            Replicapool rp = provider.getGoogleReplicapool();
            Compute gce = provider.getGoogleCompute();

            InstanceGroupManager pool = rp.instanceGroupManagers().get(provider.getContext().getAccountNumber(), "us-central1-f", inCIId).execute();
            String baseInstanceName = pool.getBaseInstanceName();
            InstanceList result = gce.instances().list(provider.getContext().getAccountNumber(), "us-central1-f").execute();
            for (Instance instance : result.getItems()) {
                if (instance.getName().startsWith(baseInstanceName + "-")) {
                    if (null != instance.getNetworkInterfaces()) {
                        for (NetworkInterface net : instance.getNetworkInterfaces()) {
                            nets.add(net.getNetwork().replaceAll(".*/", ""));
                        }
                    }
                }
            }
            return nets;
        } catch ( IOException e ) {
            e.printStackTrace();
        } finally{
            APITrace.end();
        }
        return null;
    }

    /*
     * Create a replicaPool based on options in CIProvisionOptions options
     * @see org.dasein.cloud.ci.ConvergedInfrastructureSupport#provision(org.dasein.cloud.ci.CIProvisionOptions)
     */
    @Override
    public ConvergedInfrastructure provision(CIProvisionOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.provision");
        Replicapool rp = provider.getGoogleReplicapool();
        try {
            ProviderContext ctx = provider.getContext();
            InstanceGroupManager content = new InstanceGroupManager();
            content.setBaseInstanceName(getCapabilities().getConvergedInfrastructureNamingConstraints().convertToValidName(options.getBaseInstanceName(), Locale.US));
            content.setDescription(options.getDescription());
            content.setInstanceTemplate("https://www.googleapis.com/compute/v1/projects/" + ctx.getAccountNumber() + "/global/instanceTemplates/" + options.getInstanceTemplate());
            content.setName(getCapabilities().getConvergedInfrastructureNamingConstraints().convertToValidName(options.getName(), Locale.US));
            String region = options.getZone().replaceFirst("-.$", "");
            //content.setTargetPools(targetPools);
            Operation job = rp.instanceGroupManagers().insert(ctx.getAccountNumber(), options.getZone(), options.getSize(), content).execute();
            GoogleMethod method = new GoogleMethod(provider);
            method.getCIOperationComplete(ctx, job, GoogleOperationType.ZONE_OPERATION, region, options.getZone());
            return ConvergedInfrastructure.getInstance(ctx.getAccountNumber(), region, options.getZone(), options.getBaseInstanceName(), ConvergedInfrastructureState.RUNNING, options.getName(), options.getDescription(), options.getInstanceTemplate());
        } catch ( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally{
            APITrace.end();
        }
        return null;
    }

    @Override
    public void terminate(String ciId, String explanation) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "GoogleConvergedInfrastructure.terminate");
        ProviderContext ctx = provider.getContext();

        try {
             Replicapool rp = provider.getGoogleReplicapool();
             for (ConvergedInfrastructure ci : listConvergedInfrastructures(null)) {
                 if (ci.getName().equals(ciId)) {
                     Operation job = rp.instanceGroupManagers().delete(provider.getContext().getAccountNumber(), ci.getProviderDataCenterId(), ciId).execute();
                     GoogleMethod method = new GoogleMethod(provider);
                     method.getCIOperationComplete(ctx, job, GoogleOperationType.ZONE_OPERATION, "us-central1", ci.getProviderDataCenterId());
                 }
             }
        } catch ( IOException e ) {
            e.printStackTrace();

        } finally {
            APITrace.end();
        }
    }

    @Override
    public boolean hasConvergedHttpLoadBalancerSupport() {
        return true;
    }
}
