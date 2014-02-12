package org.dasein.cloud.google.util;

import com.google.api.services.compute.Compute;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Endpoint URL provider, which is used for easy manipulation of Google resources endpoints
 *
 * Note: try once again to replace with enum, but enums cannot have same method with different parameters depending on enum value
 * without code duplication (like {@link ZoneBasedResource#getEndpointUrl} or {@link GlobalResource#getEndpointUrl}).
 *
 * @author igoonich
 * @since 13.12.2013
 */
public final class GoogleEndpoint {

	public static final ZoneBasedResource VOLUME = new ZoneBasedResource("/disks/");
	public static final GlobalResource FIREWALL = new GlobalResource("/global/firewalls/");
	public static final GlobalResource KERNEL = new GlobalResource("/global/kernels/");
	public static final GlobalResource NETWORK = new GlobalResource("/global/networks/");
	public static final GlobalResource SNAPSHOT = new GlobalResource("/global/snapshots/");
	public static final GlobalResource ZONE = new GlobalResource("/zones/");
	public static final GlobalResource REGION = new GlobalResource("/regions/");
	public static final ZoneBasedResource SERVER = new ZoneBasedResource("/instances/");
	public static final ZoneBasedResource GLOBAL_OPERATION = new ZoneBasedResource("/global/operations/");
	public static final ZoneBasedResource MACHINE_TYPE = new ZoneBasedResource("/machineTypes/");
	public static final ZoneBasedResource OPERATION = new ZoneBasedResource("/operations/");
	public static final ImageGoogleResource IMAGE = new ImageGoogleResource("/global/images/");

	private static abstract class AbstractGoogleEndpoint {
		protected String restUrl;

		protected AbstractGoogleEndpoint(String restUrl) {
			this.restUrl = checkNotNull(restUrl);
		}

		public String getResourceFromUrl(String resourceUrl) {
			// all resources have format like "https://www.googleapis.com/compute/v1/projects/google/.../{RESOURCE_NAME}"
			return StringUtils.substringAfterLast(checkNotNull(resourceUrl), "/");
		}
	}

	public static class GlobalResource extends AbstractGoogleEndpoint {
		private GlobalResource(String restUrl) {
			super(restUrl);
		}

		public String getEndpointUrl(@Nonnull String resourceId, @Nonnull String projectId) {
			return Compute.DEFAULT_BASE_URL + projectId + restUrl + resourceId;
		}
	}

	/**
	 * Resources which depends on specific data center
	 */
	public static class ZoneBasedResource extends AbstractGoogleEndpoint {
		private ZoneBasedResource(String restUrl) {
			super(restUrl);
		}

		public String getEndpointUrl(@Nonnull String resourceId, @Nonnull String projectId, @Nonnull String zoneId) {
			return Compute.DEFAULT_BASE_URL + projectId + "/zones/" + zoneId + restUrl + resourceId;
		}
	}

	/**
	 * Google resource which concatenates resource ID and project ID
	 */
	public static class ImageGoogleResource extends AbstractGoogleEndpoint {
		private static final String ID_SEPARATOR = ":";
		private Pattern idPattern;
		private Pattern urlPattern;

		private ImageGoogleResource(String restUrl) {
			super(restUrl);
			this.idPattern = Pattern.compile("(.*)" + ID_SEPARATOR + "(.*)");
			this.urlPattern = Pattern.compile(Compute.DEFAULT_BASE_URL + "(.*)" + restUrl + "(.*)");
		}

		public String getEndpointUrl(@Nonnull String resourceId) {
			Matcher matcher = idPattern.matcher(resourceId);
			if (!matcher.find()) {
				throw new IllegalArgumentException("Resource ID [" + resourceId + "] doesn't match pattern " + idPattern);
			}
			String projectId = matcher.group(1);
			String realResourceId = matcher.group(2);
			return Compute.DEFAULT_BASE_URL + projectId + restUrl + realResourceId;
		}

		@Override
		public String getResourceFromUrl(String resourceUrl) {
			Matcher matcher = urlPattern.matcher(resourceUrl);
			if (!matcher.find()) {
				throw new IllegalArgumentException("Resource URL [" + resourceUrl + "] doesn't match pattern " + urlPattern);
			}
			String projectId = matcher.group(1);
			String resourceId = matcher.group(2);
			return projectId + ID_SEPARATOR + resourceId;
		}
	}

	private GoogleEndpoint() {
		throw new AssertionError();
	}

}
