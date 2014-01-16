package org.dasein.cloud.google.util.model;

import com.google.api.client.util.DateTime;
import com.google.api.services.compute.model.*;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.GoogleDiskSupport;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.network.NICCreateOptions;
import org.dasein.cloud.network.RawAddress;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

import static org.dasein.cloud.compute.VMLaunchOptions.NICConfig;

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

	/**
	 * Creates google {@link Instance} from dasein {@link VMLaunchOptions} and provider context
	 *
	 * NB: Firewall property "targetTags" is used for managing assigned instances. For details about target tags please refer to <a
	 * href="https://developers.google.com/compute/docs/networking#firewalls">firewalls doc</a>
	 *
	 * @param withLaunchOptions dasein launch options
	 * @param context           provider context
	 * @return google instance object
	 */
	public static Instance from(VMLaunchOptions withLaunchOptions, ProviderContext context) {
		Preconditions.checkNotNull(withLaunchOptions);
		Preconditions.checkNotNull(context);

		Instance googleInstance = new Instance();
		googleInstance.setName(withLaunchOptions.getHostName());
		googleInstance.setDescription(withLaunchOptions.getDescription());

		// TODO: align with Cameron if we support default values for zones
		googleInstance.setZone(StringUtils.defaultIfBlank(withLaunchOptions.getDataCenterId(),
				context.getRegionId() + DEFAULT_INSTANCE_ZONE_TYPE));

		googleInstance.setMachineType(GoogleEndpoint.MACHINE_TYPE.getEndpointUrl(withLaunchOptions.getStandardProductId(),
				context.getAccountNumber(), googleInstance.getZone()));

		if (withLaunchOptions.getNetworkInterfaces() != null) {
			List<NetworkInterface> networkInterfaces = new ArrayList<NetworkInterface>();
			NICConfig[] nicConfigs = withLaunchOptions.getNetworkInterfaces();
			for (NICConfig nicConfig : nicConfigs) {
				NICCreateOptions createOpts = nicConfig.nicToCreate;
				String staticIp = createOpts.getIpAddress();

				NetworkInterface networkInterface = new NetworkInterface();
				networkInterface.setName(nicConfig.nicId);
				networkInterface.setNetwork(GoogleEndpoint.NETWORK.getEndpointUrl(createOpts.getVlanId(), context.getAccountNumber()));

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
			networkInterface.setNetwork(GoogleEndpoint.NETWORK.getEndpointUrl(withLaunchOptions.getVlanId(), context.getAccountNumber()));

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
			networkInterface.setNetwork(GoogleEndpoint.NETWORK.getEndpointUrl(GoogleNetworks.DEFAULT, context.getAccountNumber()));
			googleInstance.setNetworkInterfaces(Collections.singletonList(networkInterface));
		}

		if (withLaunchOptions.getKernelId() != null) {
			logger.warn("Kernels are not supported any more in GCE v1, therefore kernel [{}] won't be processed",
					withLaunchOptions.getKernelId());
		}

		// assign firewalls to instances as tags
		if (withLaunchOptions.getFirewallIds().length > 0) {
			Tags tags = new Tags();
			tags.setItems(Arrays.asList(withLaunchOptions.getFirewallIds()));
			googleInstance.setTags(tags);
		}

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

		return googleInstance;
	}

	/**
	 * Converts google {@link Instance} to dasein {@link VirtualMachine} object
	 *
	 * @param googleInstance google instance
	 * @param context        provider context
	 * @return virtual machine
	 */
	public static VirtualMachine toDaseinVirtualMachine(Instance googleInstance, ProviderContext context) {
		Preconditions.checkNotNull(googleInstance);
		Preconditions.checkNotNull(context);

		VirtualMachine virtualMachine = new VirtualMachine();

		// as was initially done always the architecture is set to I64
		// TODO: get the correct architecture based on googleInstance.getMachineType()
		virtualMachine.setArchitecture(Architecture.I64);
		virtualMachine.setPersistent(true);

		// TODO: check what to set?
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

		virtualMachine.setPlatform(Platform.guess(googleInstance.getMachineType()));

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
			// Note: google doesn't include public DNS name
		}

		// disks related properties
		List<Volume> volumes = new ArrayList<Volume>();
		for (AttachedDisk attachedDisk : googleInstance.getDisks()) {
			Volume attachedVolume = new Volume();
			attachedVolume.setName(GoogleEndpoint.VOLUME.getResourceFromUrl(attachedDisk.getSource()));
			attachedVolume.setDeviceId(attachedDisk.getDeviceName());
			attachedVolume.setProviderVolumeId(GoogleEndpoint.VOLUME.getResourceFromUrl(attachedDisk.getSource()));
			attachedVolume.setRootVolume(Boolean.TRUE.equals(attachedDisk.getBoot()));
			volumes.add(attachedVolume);
		}
		virtualMachine.setVolumes(volumes.toArray(new Volume[volumes.size()]));

		// metadata properties
		Metadata metadata = googleInstance.getMetadata();
		if (metadata.getItems() != null) {
			for (Metadata.Items items : metadata.getItems()) {
				virtualMachine.addTag(items.getKey(), items.getValue());
			}
		}

		// google tags as firewalls
		Tags tags = googleInstance.getTags();
		if (tags != null && tags.getItems() != null) {
			List<String> items = tags.getItems();
			virtualMachine.setProviderFirewallIds(items.toArray(new String[items.size()]));
		}

		virtualMachine.setIpForwardingAllowed(Boolean.TRUE.equals(googleInstance.getCanIpForward()));

		// TODO: check - machine type should have the same zone as instance or not?
		virtualMachine.setProductId(GoogleEndpoint.MACHINE_TYPE.getResourceFromUrl(googleInstance.getMachineType()));
		virtualMachine.setCreationTimestamp(DateTime.parseRfc3339(googleInstance.getCreationTimestamp()).getValue());

		// TODO: check what to set?
		virtualMachine.setClonable(false);

		return virtualMachine;
	}

	public static ResourceStatus toDaseinResourceStatus(Instance googleInstance) {
		InstanceStatus instanceStatus = InstanceStatus.fromString(googleInstance.getStatus());
		return new ResourceStatus(googleInstance.getName(), instanceStatus.asDaseinStatus());
	}

	/**
	 * Returns root volume for the virtual machine
	 *
	 * @param virtualMachine virtual machine
	 * @return root volume if exists, {@code null} otherwise
	 */
	@Nullable
	public static Volume getRootVolume(VirtualMachine virtualMachine) {
		for (Volume volume : virtualMachine.getVolumes()) {
			if (volume.isRootVolume()) {
				return volume;
			}
		}
		return null;
	}

	/**
	 * Strategy for converting google instance to dasein virtual machine
	 */
	public static final class InstanceToDaseinVMConverter implements Function<Instance, VirtualMachine> {
		private ProviderContext context;
		private GoogleDiskSupport googleDiskSupport;

		public InstanceToDaseinVMConverter(ProviderContext context) {
			this.context = context;
		}

		/**
		 * Include machine image type to the {@link VirtualMachine} object while converting
		 *
		 * This method requires a google disk service in order to fetch boot disk information, because there is not way to know image source from
		 * the google instance object
		 *
		 * @param googleDiskSupport google disk support service
		 * @return same converter (builder variation)
		 */
		public InstanceToDaseinVMConverter withMachineImage(GoogleDiskSupport googleDiskSupport) {
			this.googleDiskSupport = Preconditions.checkNotNull(googleDiskSupport);
			return this;
		}

		@Override
		@Nullable
		public VirtualMachine apply(@Nullable Instance from) {
			VirtualMachine virtualMachine = GoogleInstances.toDaseinVirtualMachine(from, context);
			if (googleDiskSupport != null) {
				includeMachineImageId(virtualMachine);
			}
			return virtualMachine;
		}

		private void includeMachineImageId(VirtualMachine virtualMachine) {
			Preconditions.checkNotNull(virtualMachine);
			Preconditions.checkNotNull(virtualMachine.getVolumes());
			Volume rootVolume = getRootVolume(virtualMachine);
			if (rootVolume != null) {
				try {
					String sourceImage = googleDiskSupport.getVolumeImage(rootVolume.getProviderVolumeId(),
							virtualMachine.getProviderDataCenterId());
					if (sourceImage != null) {
						virtualMachine.setProviderMachineImageId(GoogleEndpoint.IMAGE.getResourceFromUrl(sourceImage));
					} else {
						logger.warn("Source image name is not present in boot disk '{}', probably that image was obsoleted",
								rootVolume.getProviderVolumeId());
					}
				} catch (Exception e) {
					logger.error("Failed to retrieve boot disk [" + rootVolume.getProviderVolumeId() + "] for instance ["
							+ virtualMachine.getName() + "]", e);
				}
			}
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

		public static IdentityFunction getInstance() {
			return INSTANCE;
		}

		@Nullable
		@Override
		public Instance apply(@Nullable Instance input) {
			return input;
		}
	}

}
