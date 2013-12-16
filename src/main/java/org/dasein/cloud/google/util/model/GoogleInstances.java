package org.dasein.cloud.google.util.model;

import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.*;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.network.NICCreateOptions;
import org.dasein.cloud.network.RawAddress;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.dasein.cloud.compute.VMLaunchOptions.NICConfig;
import static org.dasein.cloud.compute.VMLaunchOptions.VolumeAttachment;

/**
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleInstances {

	private static final Logger logger = Google.getLogger(GoogleInstances.class);

	/**
	 * Data center extension to be used by default
	 */
	private static final String DEFAULT_INSTANCE_ZONE_TYPE = "a";

	public enum InstanceStatus {
		PROVISIONING, STAGING, RUNNING, STOPPING, STOPPED, TERMINATED, UNKNOWN;

		public static InstanceStatus fromString(String status) {
			try {
				return valueOf(status);
			} catch (IllegalArgumentException e) {
				logger.warn("Unknown google instance status [" + status + "] will be mapped as 'UNKNOWN'");
				return UNKNOWN;
			}
		}

		public VmState asDaseinStatus() {
			switch (this) {
				case PROVISIONING:
					return VmState.PENDING;
				case STAGING:
					return VmState.PENDING;
				case RUNNING:
					return VmState.RUNNING;
				case STOPPING:
					return VmState.STOPPING;
				case STOPPED:
					return VmState.STOPPED;
				case TERMINATED:
					return VmState.TERMINATED;
				default:
					// for any unknown status use "PENDING"
					return VmState.PENDING;
			}
		}
	}

	public static Instance from(VMLaunchOptions withLaunchOptions, ProviderContext context, String bootVolume) {
		Preconditions.checkNotNull(withLaunchOptions);
		Preconditions.checkNotNull(context);

		Instance googleInstance = new Instance();
		googleInstance.setName(withLaunchOptions.getHostName());
		googleInstance.setDescription(withLaunchOptions.getDescription());

		// TODO: align if we support default values for zones
		googleInstance.setZone(StringUtils.defaultIfBlank(withLaunchOptions.getDataCenterId(),
				context.getRegionId() + DEFAULT_INSTANCE_ZONE_TYPE));

		// TODO: no 'image' field in google instance object...
//		googleInstance.setImage(GoogleEndpoint.IMAGE.getEndpointUrl() + withLaunchOptions.getMachineImageId());
		googleInstance.setMachineType(GoogleEndpoint.MACHINE_TYPE.getEndpointUrl(context.getAccountNumber(), googleInstance.getZone())
				+ withLaunchOptions.getStandardProductId());

		if (withLaunchOptions.getNetworkInterfaces() != null) {
			List<NetworkInterface> networkInterfaces = new ArrayList<NetworkInterface>();
			NICConfig[] nicConfigs = withLaunchOptions.getNetworkInterfaces();
			for (NICConfig nicConfig : nicConfigs) {
				NICCreateOptions createOpts = nicConfig.nicToCreate;
				String staticIp = createOpts.getIpAddress();

				NetworkInterface networkInterface = new NetworkInterface();
				networkInterface.setName(nicConfig.nicId);
				networkInterface.setNetwork(GoogleEndpoint.NETWORK.getEndpointUrl(context.getAccountNumber())
						+ createOpts.getVlanId());

				if (staticIp != null) {
					List<AccessConfig> accessConfigs = new ArrayList<AccessConfig>();
					AccessConfig accessConfig = new AccessConfig();
					accessConfig.setName(createOpts.getName());
					accessConfig.setKind("compute#accessConfig");
					accessConfig.setType("ONE_TO_ONE_NAT");
					accessConfig.setNatIP(staticIp);
					accessConfigs.add(accessConfig);
					networkInterface.setAccessConfigs(accessConfigs);
				}

				networkInterfaces.add(networkInterface);
			}
			googleInstance.setNetworkInterfaces(networkInterfaces);
		} else if (withLaunchOptions.getVlanId() != null) {
			NetworkInterface networkInterface = new NetworkInterface();
			networkInterface.setName(withLaunchOptions.getVlanId());
			networkInterface.setNetwork(GoogleEndpoint.NETWORK.getEndpointUrl(context.getAccountNumber()) + withLaunchOptions.getVlanId());

			String[] staticIps = withLaunchOptions.getStaticIpIds();
			List<AccessConfig> accessConfigs = new ArrayList<AccessConfig>();
			for (String staticIp : staticIps) {
				AccessConfig accessConfig = new AccessConfig();
				accessConfig.setKind("compute#accessConfig");
				accessConfig.setName(staticIp);
				accessConfig.setType("ONE_TO_ONE_NAT");
				accessConfig.setNatIP(staticIp);
				accessConfigs.add(accessConfig);
			}
			networkInterface.setAccessConfigs(accessConfigs);

			googleInstance.setNetworkInterfaces(Collections.singletonList(networkInterface));
		} else {
			NetworkInterface networkInterface = new NetworkInterface();
			networkInterface.setName(GoogleNetworks.DEFAULT);
			networkInterface.setNetwork(GoogleEndpoint.NETWORK.getEndpointUrl(context.getAccountNumber()) + GoogleNetworks.DEFAULT);
			googleInstance.setNetworkInterfaces(Collections.singletonList(networkInterface));
		}

		// Kernels are not supported any more in v1
		/*
		if (withLaunchOptions.getKernelId() != null) {
			String kernel = method.getEndpoint(ctx, GoogleMethod.KERNEL) + "/" + withLaunchOptions.getKernelId();
			payload.put("kernel", kernel);
		}
		*/

		// initialize google instance metadata
		Map<String, Object> metaData = withLaunchOptions.getMetaData();
		List<Metadata.Items> itemsList = new ArrayList<Metadata.Items>();
		for (String key : metaData.keySet()) {
			Metadata.Items keyValuePair = new Metadata.Items();
			keyValuePair.set(key, metaData.get(key));
			itemsList.add(keyValuePair);
		}

		Metadata googleMetadata = new Metadata();
		googleMetadata.setKind("compute#metadata");
		googleMetadata.setItems(itemsList);
		googleInstance.setMetadata(googleMetadata);

		// initialize google disk attachments
		List<AttachedDisk> attachedDisks = new ArrayList<AttachedDisk>();

		// initialize boot disk
		AttachedDisk googleBootDisk = new AttachedDisk();
		googleBootDisk.setBoot(true);
		googleBootDisk.setType("PERSISTENT");
		googleBootDisk.setMode("READ_WRITE");
		googleBootDisk.setSource(GoogleEndpoint.VOLUME.getEndpointUrl(context.getAccountNumber(), googleInstance.getZone()) + bootVolume);
		attachedDisks.add(googleBootDisk);

		// include additional disks
		VolumeAttachment[] attachments = withLaunchOptions.getVolumes();
		if (!attachedDisks.isEmpty()) {
			for (VolumeAttachment attachment : attachments) {
				AttachedDisk googleDisk = new AttachedDisk();
				VolumeCreateOptions createOptions = attachment.volumeToCreate;
				if (createOptions != null) {
					// ephemeral
					googleDisk.setType("EPHEMERAL");
					googleDisk.setMode("READ_WRITE");
					googleDisk.setDeviceName(createOptions.getName());
				} else {
					// persistent disk
					googleDisk.setType("PERSISTENT");
					googleDisk.setMode("READ_WRITE");
					googleDisk.setSource(GoogleEndpoint.VOLUME.getEndpointUrl(context.getAccountNumber(), googleInstance.getZone())
							+ attachment.existingVolumeId);
					googleDisk.setDeviceName(attachment.deviceId);
				}

				attachedDisks.add(googleDisk);
			}
		}

		googleInstance.setDisks(attachedDisks);

		return googleInstance;
	}

	public static VirtualMachine toDaseinVirtualMachine(Instance googleInstance, ProviderContext context) {
		Preconditions.checkNotNull(googleInstance);
		Preconditions.checkNotNull(context);

		VirtualMachine virtualMachine = new VirtualMachine();

		// as was initially done always the architecture is set to I32
		// TODO: get the correct architecture based on googleInstance.getMachineType()
		virtualMachine.setArchitecture(Architecture.I64);
		virtualMachine.setPersistent(true);
		// TODO check what to set
		virtualMachine.setImagable(false);
		virtualMachine.setProviderSubnetId(null);

		virtualMachine.setProviderOwnerId(context.getAccountNumber());
		virtualMachine.setProviderRegionId(context.getRegionId());

		virtualMachine.setName(googleInstance.getName());
		virtualMachine.setProviderVirtualMachineId(googleInstance.getName());
		virtualMachine.setDescription(googleInstance.getDescription());
		virtualMachine.setProviderDataCenterId(GoogleEndpoint.ZONE.getResourceFromUrl(googleInstance.getZone()));

		InstanceStatus instanceStatus = InstanceStatus.fromString(googleInstance.getStatus());
		virtualMachine.setCurrentState(instanceStatus.asDaseinStatus());
		if (InstanceStatus.RUNNING.equals(instanceStatus)) {
			virtualMachine.setRebootable(true);
		}

		/*
			if (json.has("image")) {
				String os = (String) json.get("image");
				os = GoogleMethod.getResourceName(os, GoogleMethod.IMAGE);
				vm.setProviderMachineImageId(os);
				vm.setPlatform(Platform.guess(os));
			}
		 */
		virtualMachine.setPlatform(Platform.guess(googleInstance.getMachineType()));
		// TODO: not clear how to get image ID
//		virtualMachine.setProviderMachineImageId(???);


		// network related properties (expected to be only one network interface)
		List<NetworkInterface> networkInterfaces = googleInstance.getNetworkInterfaces();
		NetworkInterface currentNetworkInterface = networkInterfaces.get(0);

		virtualMachine.setProviderVlanId(GoogleEndpoint.NETWORK.getResourceFromUrl(currentNetworkInterface.getNetwork()));
		virtualMachine.setPrivateAddresses(new RawAddress(currentNetworkInterface.getNetworkIP()));

		if (currentNetworkInterface.getAccessConfigs() != null) {
			List<RawAddress> addresses = new ArrayList<RawAddress>();
			for (AccessConfig accessConfig : currentNetworkInterface.getAccessConfigs()) {
				addresses.add(new RawAddress(accessConfig.getNatIP()));
			}
			virtualMachine.setPublicAddresses(addresses.toArray(new RawAddress[0]));
		}

		// disks related properties
		List<Volume> volumes = new ArrayList<Volume>();
		for (AttachedDisk attachedDisk : googleInstance.getDisks()) {
			Volume attachedVolume = new Volume();
			attachedVolume.setName(attachedDisk.getDeviceName());
			attachedVolume.setProviderVolumeId(GoogleEndpoint.VOLUME.getResourceFromUrl(attachedDisk.getSource()));
			attachedVolume.setRootVolume(Boolean.TRUE.equals(attachedDisk.getBoot()));
			volumes.add(attachedVolume);
		}
		virtualMachine.setVolumes(volumes.toArray(new Volume[0]));

		// metadata properties
		Metadata metadata = googleInstance.getMetadata();
		if (metadata.getItems() != null) {
			for (Metadata.Items items : metadata.getItems()) {
				virtualMachine.addTag(items.getKey(), items.getValue());
			}
		}

		virtualMachine.setIpForwardingAllowed(Boolean.TRUE.equals(googleInstance.getCanIpForward()));

		// TODO: check what to do with tags?
		/*
		Tags tags = googleInstance.getTags();
		if (tags.getItems() != null) {
			for (String tag : tags.getItems()) {
				virtualMachine.addTag(tag, StringUtils.EMPTY);
			}
		}
		*/

		/*
		if (json.has("machineType")) {
			String product = json.getString("machineType");
			product = GoogleMethod.getResourceName(product, GoogleMethod.MACHINE_TYPE); // FIXME: this is broken due to '/global'
			vm.setProductId(product);
		}
		*/
		// TODO: check - machine time should have the same zone as instance or not?
		virtualMachine.setProductId(GoogleEndpoint.MACHINE_TYPE.getResourceFromUrl(googleInstance.getMachineType()));
		virtualMachine.setCreationTimestamp(DateTime.parseRfc3339(googleInstance.getCreationTimestamp()).getValue());

		// TODO: check what to set
		virtualMachine.setClonable(false);

		return virtualMachine;
	}

	private static ResourceStatus toDaseinResourceStatus(Instance googleInstance) {
		InstanceStatus instanceStatus = InstanceStatus.fromString(googleInstance.getStatus());
		return new ResourceStatus(googleInstance.getName(), instanceStatus.asDaseinStatus());
	}

	/**
	 * Strategy for converting google instance to dasein virtual machine
	 */
	public static final class InstanceToDaseinVMConverter implements Function<Instance, VirtualMachine> {
		private ProviderContext context;

		public InstanceToDaseinVMConverter(ProviderContext context) {
			this.context = context;
		}

		@Override
		@Nullable
		public VirtualMachine apply(@Nullable Instance from) {
			return GoogleInstances.toDaseinVirtualMachine(from, context);
		}
	}

	/**
	 * Strategy for converting google instance to dasein resource status
	 */
	public static final class InstanceToDaseinStatusConverter implements Function<Instance, ResourceStatus> {
		private static final InstanceToDaseinStatusConverter INSTANCE = new InstanceToDaseinStatusConverter();

		public static InstanceToDaseinStatusConverter getInstance() {
			return INSTANCE;
		}

		@Nullable
		@Override
		public ResourceStatus apply(@Nullable Instance from) {
			return GoogleInstances.toDaseinResourceStatus(from);
		}
	}

	/**
	 * Default converting strategy as identity transformation
	 */
	public static class IdentityFunction implements Function<Instance, Instance> {
		private static final IdentityFunction INSTANCE = new IdentityFunction();

		public static final IdentityFunction getInstance() {
			return INSTANCE;
		}

		@Nullable
		@Override
		public Instance apply(@Nullable Instance input) {
			return input;
		}
	}

	;

}
