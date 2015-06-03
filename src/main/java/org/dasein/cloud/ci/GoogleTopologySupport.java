package org.dasein.cloud.ci;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ci.Topology.VLANDevice;
import org.dasein.cloud.ci.Topology.VMDevice;
import org.dasein.cloud.ci.TopologyProvisionOptions.Disk;
import org.dasein.cloud.ci.TopologyProvisionOptions.Network;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.compute.server.ServerSupport;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute.InstanceTemplates;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.InstanceTemplateList;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.Tags;

public class GoogleTopologySupport extends AbstractTopologySupport<Google> {
    private InstanceTemplates instanceTemplates = null;;

    public GoogleTopologySupport(Google provider) {
        super(provider);
        try {
            instanceTemplates = getProvider().getGoogleCompute().instanceTemplates();

        } catch ( CloudException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch ( InternalException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public String getProviderTermForTopology(Locale locale) {
        return "Instance Template";
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public Iterable<Topology> listTopologies(TopologyFilterOptions options) throws CloudException, InternalException {
        List<Topology> topologies = new ArrayList<Topology>();
        try {
            InstanceTemplateList templateList = instanceTemplates.list(getContext().getAccountNumber()).execute();
            for (InstanceTemplate template : templateList.getItems()) {
                InstanceProperties templateProperties = template.getProperties();
                VMDevice vmDevices = null;
                String machineType = templateProperties.getMachineType();
                ServerSupport server = new ServerSupport(getProvider());
                Iterable<VirtualMachineProduct> vmProducts = server.listProducts(Architecture.I64, "us-central1-f");
                for (VirtualMachineProduct vmProduct: vmProducts) {
                    if (vmProduct.getName().equals(machineType)) {
                        vmDevices = VMDevice.getInstance(machineType, machineType, vmProduct.getCpuCount(), vmProduct.getRamSize(), (String) null);
                    }
                }

                List<NetworkInterface> networkInterfaces = templateProperties.getNetworkInterfaces();
                String name = null;
                String deviceId = null;
                for (NetworkInterface networkInterface: networkInterfaces) {
                    deviceId = networkInterface.getNetwork();
                    name = deviceId.replaceAll(".*/", "");
                }

                Topology topology = Topology.getInstance(getContext().getAccountNumber(), null, template.getName(), TopologyState.ACTIVE, template.getName(), template.getDescription());

                if (null != vmDevices) {
                    topology = topology.withVirtualMachines(vmDevices);
                }

                if ((null != name) && (null != deviceId)) {
                    topology = topology.withVLANs(VLANDevice.getInstance(deviceId, name));
                }

                if ((null == options) || (options.matches(topology))) {
                    topologies.add(topology); 
                }
            }
        } catch ( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return topologies;
    }

    @Override
    public boolean createTopology(@Nonnull TopologyProvisionOptions withTopologyOptions) throws CloudException, InternalException {
        InstanceTemplate newInstanceTemplate = new InstanceTemplate();

        newInstanceTemplate.setName(getCapabilities().getTopologyNamingConstraints().convertToValidName(withTopologyOptions.getProductName(), Locale.US));
        newInstanceTemplate.setDescription(withTopologyOptions.getProductDescription());
        InstanceProperties instanceProperties = new InstanceProperties();
        instanceProperties.setCanIpForward(withTopologyOptions.getCanIpForward());
        instanceProperties.setDescription(withTopologyOptions.getProductDescription());
        instanceProperties.setMachineType(withTopologyOptions.getMachineType());

        List<Disk> disks = withTopologyOptions.getDiskArray();
        List<AttachedDisk> attachedDisks = new ArrayList<AttachedDisk>();
        for (Disk topologyDisk : disks) {
            AttachedDisk disk =  new AttachedDisk();
            disk.setAutoDelete(topologyDisk.getAutoDelete());
            disk.setBoot(topologyDisk.getBootable());
            disk.setDeviceName(withTopologyOptions.getProductName());
            AttachedDiskInitializeParams attachedDiskInitializeParams = new AttachedDiskInitializeParams();
            attachedDiskInitializeParams.setSourceImage(topologyDisk.getDeviceSource());
            attachedDiskInitializeParams.setDiskType(topologyDisk.getDeviceType().toString());
            disk.setInitializeParams(attachedDiskInitializeParams);
            attachedDisks.add(disk);
        }
        instanceProperties.setDisks(attachedDisks);  //must provide at least one AttachedDisk definition.

        List<Network> topologyNetworksList = withTopologyOptions.getNetworkArray();
        List<NetworkInterface> networks = new ArrayList<NetworkInterface>();
        for (Network topologyNetwork : topologyNetworksList) {
            NetworkInterface networkInterface = new NetworkInterface();
            networkInterface.setName(topologyNetwork.getNetworkName());
            networkInterface.setNetwork(topologyNetwork.getNetworkSelfUrl());

            List<TopologyProvisionOptions.AccessConfig> topologyNetworksAccessConfig = topologyNetwork.getAccessConfig();
            List<AccessConfig> accessConfig = new ArrayList<AccessConfig>();

            for (TopologyProvisionOptions.AccessConfig topologyAccessConfig : topologyNetworksAccessConfig) {
                AccessConfig cfg = new AccessConfig();
                cfg.setName(topologyAccessConfig.getName());
                cfg.setKind(topologyAccessConfig.getKind());
                cfg.setType(topologyAccessConfig.getType());
                accessConfig.add(cfg);
            }
            networkInterface.setAccessConfigs(accessConfig);
            networks.add(networkInterface);
        }
        instanceProperties.setNetworkInterfaces(networks);

        Tags tags = new Tags();
        tags.setItems(withTopologyOptions.getTags());
        instanceProperties.setTags(tags);

        String[] sshKeys = withTopologyOptions.getSshKeys();
        Metadata metadata = new Metadata();
        List<Items> metadataItems = new ArrayList<Items>();
        for (String sshKey : sshKeys) {
            Metadata.Items  item = new Metadata.Items() ;
            item.setKey("sshKeys");
            item.setValue(sshKey);
            metadataItems.add(item);
        }

        Map<String, String> metaDataItems = withTopologyOptions.getMetadata();
        for (String itemKey : metaDataItems.keySet()) {
            Metadata.Items item = new Metadata.Items() ;
            item.setKey(itemKey);
            item.setValue(metaDataItems.get(itemKey));
            metadataItems.add(item);
        }

        metadata.setItems(metadataItems);
        instanceProperties.setMetadata(metadata);

        Scheduling scheduling = new Scheduling();
        scheduling.setAutomaticRestart(withTopologyOptions.getAutomaticRestart());
        scheduling.setOnHostMaintenance(withTopologyOptions.getMaintenenceAction().toString());
        instanceProperties.setScheduling(scheduling);

/*
        List<ServiceAccount> serviceAccounts = new ArrayList<ServiceAccount>();
        ServiceAccount serviceAccount = new ServiceAccount();
        serviceAccount.setEmail("email");
        List<String> scopes = new ArrayList<String>();
        serviceAccount.setScopes(scopes);
        serviceAccounts.add(serviceAccount);
        instanceProperties.setServiceAccounts(serviceAccounts);

*/
        newInstanceTemplate.setProperties(instanceProperties);
        try {
            Operation job = instanceTemplates.insert(getContext().getAccountNumber(), newInstanceTemplate).execute();
            GoogleMethod method = new GoogleMethod(getProvider());
            method.getOperationComplete(getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "");
        } catch (IOException ex) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(ex.getMessage());
        }
        return true;
    }

    @Override
    public boolean removeTopologies(@Nonnull String[] topologyIds) throws CloudException, InternalException {
        for (String topologyName: topologyIds) {
            try {
                Operation job = instanceTemplates.delete(getContext().getAccountNumber(), topologyName).execute();
                GoogleMethod method = new GoogleMethod(getProvider());
                method.getOperationComplete(getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "");
            } catch (IOException ex) {
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException(ex.getMessage());
            }
        }
        return true;
    }

    private transient volatile GCETopologyCapabilities capabilities;

    @Override
    public @Nonnull GCETopologyCapabilities getCapabilities() throws CloudException, InternalException {
        if( capabilities == null ) {
            capabilities = new GCETopologyCapabilities(getProvider());
        }
        return capabilities;
    }

}
