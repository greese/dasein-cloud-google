package org.dasein.cloud.ci;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ci.Topology.VLANDevice;
import org.dasein.cloud.ci.Topology.VMDevice;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.compute.server.ServerSupport;
import org.dasein.util.uom.storage.Storage;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.InstanceTemplates;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.InstanceTemplateList;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Metadata.Items;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.api.services.compute.model.Tags;

public class GoogleTopologySupport extends AbstractTopologySupport<Google> {

    private Google provider;
    private InstanceTemplates instanceTemplates = null;;
    public GoogleTopologySupport(Google provider) {
        super(provider);
        this.provider = provider;
        try {
            instanceTemplates = provider.getComputeServices().getProvider().getGoogleCompute().instanceTemplates();
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
            InstanceTemplateList templateList = instanceTemplates.list(provider.getContext().getAccountNumber()).execute();
            for (InstanceTemplate template : templateList.getItems()) {
                InstanceProperties templateProperties = template.getProperties();
                VMDevice vmDevices = null;
                String machineType = templateProperties.getMachineType();
                ServerSupport server = new ServerSupport(provider);
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

                Topology topology = Topology.getInstance(provider.getContext().getAccountNumber(), null, template.getName(), TopologyState.ACTIVE, template.getName(), template.getDescription());

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
    public boolean createTopology() throws CloudException, InternalException {
        InstanceTemplate newInstanceTemplate = new InstanceTemplate();

        String name = "instance-template-1";
        newInstanceTemplate.setName(name);
        newInstanceTemplate.setDescription("description");

        InstanceProperties instanceProperties = new InstanceProperties();
        instanceProperties.setCanIpForward(false);
        instanceProperties.setDescription("description");


        List<AttachedDisk> disks = new ArrayList<AttachedDisk>();
        AttachedDisk disk =  new AttachedDisk();
        disk.setAutoDelete(true);
        disk.setBoot(true);
        disk.setDeviceName(name);
        disk.setSource("https://www.googleapis.com/compute/v1/projects/debian-cloud/global/images/backports-debian-7-wheezy-v20150127");
        disk.setType("pd-standard");
        disks.add(disk);
        instanceProperties.setDisks(disks);  //must provide at least one AttachedDisk definition.


        String machineType = "f1-micro";

        instanceProperties.setMachineType(machineType);  //Instance properties must provide a machine type.
/*

        Metadata metadata = new Metadata();
        List<Items> metadataItems = new ArrayList<Items>();
        Items item = new Items();
        item.set("name", "value object");
        metadataItems.add(item);
        metadata.setItems(metadataItems);
        instanceProperties.setMetadata(metadata);
*/

        List<NetworkInterface> networks = new ArrayList<NetworkInterface>();
        NetworkInterface networkInterface = new NetworkInterface();
        List<AccessConfig> accessConfig = new ArrayList<AccessConfig>();
        AccessConfig cfg = new AccessConfig();
        cfg.setName("External NAT");
        cfg.setNatIP("natIP");
        cfg.setType("ONE_TO_ONE_NAT");

        accessConfig.add(cfg );
        networkInterface.setAccessConfigs(accessConfig);
        networkInterface.setName("name");
        networkInterface.setNetwork("https://www.googleapis.com/compute/v1/projects/qa-project-2/global/networks/default");

        networks.add(networkInterface );
        instanceProperties.setNetworkInterfaces(networks);
/*

        Scheduling scheduling = new Scheduling();
        scheduling.setAutomaticRestart(true);
        scheduling.setOnHostMaintenance("arg");
        instanceProperties.setScheduling(scheduling);

        List<ServiceAccount> serviceAccounts = new ArrayList<ServiceAccount>();
        ServiceAccount serviceAccount = new ServiceAccount();
        serviceAccount.setEmail("email");
        List<String> scopes = new ArrayList<String>();
        serviceAccount.setScopes(scopes);
        serviceAccounts.add(serviceAccount);
        instanceProperties.setServiceAccounts(serviceAccounts);
        Tags tags = new Tags();
        List<String> tagItems = new ArrayList<String>();
        tags.setItems(tagItems);
        instanceProperties.setTags(tags);

*/
        newInstanceTemplate.setProperties(instanceProperties);
        try {
            Operation job = instanceTemplates.insert(provider.getContext().getAccountNumber(), newInstanceTemplate).execute();
            GoogleMethod method = new GoogleMethod(provider);
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "");
        } catch (IOException ex) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(ex.getMessage());
        }
        return true;
    }

/*
{
  "kind": "compute#instanceTemplate",
  "id": "6114067345840165923",
  "creationTimestamp": "2015-02-09T20:23:55.625-08:00",
  "selfLink": "https://www.googleapis.com/compute/v1/projects/qa-project-2/global/instanceTemplates/instance-template-1",
  "name": "instance-template-1",
  "description": "",
  "properties": {
    "tags": {
      "items": [
        "http-server"
      ]
    },
    "machineType": "f1-micro",
    "canIpForward": false,
    "networkInterfaces": [
      {
        "network": "https://www.googleapis.com/compute/v1/projects/qa-project-2/global/networks/default",
        "accessConfigs": [
          {
            "kind": "compute#accessConfig",
            "type": "ONE_TO_ONE_NAT",
            "name": "External NAT"
          }
        ]
      }
    ],
    "disks": [
      {
        "kind": "compute#attachedDisk",
        "type": "PERSISTENT",
        "mode": "READ_WRITE",
        "deviceName": "instance-template-1",
        "boot": true,
        "initializeParams": {
          "sourceImage": "https://www.googleapis.com/compute/v1/projects/debian-cloud/global/images/backports-debian-7-wheezy-v20150127",
          "diskType": "pd-standard"
        },
        "autoDelete": true
      }
    ],
    "metadata": {
      "kind": "compute#metadata"
    },
    "serviceAccounts": [
      {
        "email": "default",
        "scopes": [
          "https://www.googleapis.com/auth/devstorage.read_only"
        ]
      }
    ],
    "scheduling": {
      "onHostMaintenance": "MIGRATE",
      "automaticRestart": true
    }
  }
}

 */
    
    @Override
    public boolean removeTopologies(@Nonnull String[] topologyIds) throws CloudException, InternalException {
        for (String topologyName: topologyIds) {
            try {
                Operation job = instanceTemplates.delete(provider.getContext().getAccountNumber(), topologyName).execute();
                GoogleMethod method = new GoogleMethod(provider);
                method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "");
            } catch (IOException ex) {
                if (ex.getClass() == GoogleJsonResponseException.class) {
                    GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                    throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
                } else
                    throw new CloudException(ex.getMessage());
            }
        }
        throw new InternalException("Operation not supported for this cloud");
    }
}
