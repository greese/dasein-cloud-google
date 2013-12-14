package org.dasein.cloud.google.util;

import com.google.api.services.compute.Compute;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;

/**
 * Endpoint URL provider
 *
 * Note: probably need to be replaced with enum, but enum cannot have same method
 * with different parameters depending on enum value
 *
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleEndpoint {

	public static final ZoneBasedResource VOLUME = new ZoneBasedResource("/disks");
	public static final GlobalResource FIREWALL = new GlobalResource("/global/firewalls");
	public static final PublicGoogleResource IMAGE = new PublicGoogleResource("/global/images");
	public static final GlobalResource KERNEL = new GlobalResource("/global/kernels");
	public static final GlobalResource NETWORK = new GlobalResource("/global/networks");
	public static final GlobalResource SNAPSHOT = new GlobalResource("/global/snapshots");
	public static final GlobalResource ZONE = new GlobalResource("/zones");
	public static final ZoneBasedResource SERVER = new ZoneBasedResource("/instances");
	public static final ZoneBasedResource GLOBAL_OPERATION = new ZoneBasedResource("/global/operations");
	public static final ZoneBasedResource MACHINE_TYPE = new ZoneBasedResource("/machineTypes");
	public static final ZoneBasedResource OPERATION = new ZoneBasedResource("/operations");

	private static abstract class AbstractGoogleEndpoint {
		protected String restUrl;

		protected AbstractGoogleEndpoint(String restUrl) {
			this.restUrl = Preconditions.checkNotNull(restUrl);
		}
	}

	public static class PublicGoogleResource extends AbstractGoogleEndpoint {
		private PublicGoogleResource(String restUrl) {
			super(restUrl);
		}

		public String getEndpointUrl() {
			return Compute.DEFAULT_BASE_URL + "google" + restUrl;
		}
	}

	public static class GlobalResource extends AbstractGoogleEndpoint {
		private GlobalResource(String restUrl) {
			super(restUrl);
		}

		public String getEndpointUrl(@Nonnull String projectId) {
			return Compute.DEFAULT_BASE_URL + projectId + restUrl;
		}
	}

	/**
	 * Resources which depend on specific data center
	 */
	public static class ZoneBasedResource extends AbstractGoogleEndpoint {
		private ZoneBasedResource(String restUrl) {
			super(restUrl);
		}

		public String getEndpointUrl(@Nonnull String projectId, String zoneId) {
			return Compute.DEFAULT_BASE_URL + projectId + "/zones/" + zoneId + restUrl;
		}
	}

	private GoogleEndpoint() {
		throw new AssertionError();
	}

}
