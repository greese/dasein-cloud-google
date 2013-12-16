/**
 * Copyright (C) 2012-2013 Dell, Inc
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

package org.dasein.cloud.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import org.apache.log4j.Logger;
import org.dasein.cloud.AbstractCloud;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.google.compute.GoogleCompute;
import org.dasein.cloud.google.network.GoogleNetwork;
import org.dasein.cloud.google.util.GoogleAuthUtils;
import org.dasein.cloud.google.util.HttpTransportFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Support for the Google API through Dasein Cloud. <p>Created by George Reese: 12/06/2012 9:35 AM</p>
 *
 * @author George Reese
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class Google extends AbstractCloud {

	private static final Logger logger = getLogger(Google.class);

	/**
	 * Application name for GCE dasein implementation
	 */
	private static final String GCE_DASIN_APPLICATION_NAME = "Google-Compute-Dasein-Implementation/1.0";

	/**
	 * Google Compute Engine service locator object
	 */
	private Compute googleCompute;

	public Compute getGoogleCompute() {
		return googleCompute;
	}

	@Nonnull
	private static String getLastItem(@Nonnull String name) {
		int idx = name.lastIndexOf('.');

		if (idx < 0) {
			return name;
		} else if (idx == (name.length() - 1)) {
			return "";
		}
		return name.substring(idx + 1);
	}

	@Nonnull
	public static Logger getLogger(@Nonnull Class<?> cls) {
		String pkg = getLastItem(cls.getPackage().getName());

		if (pkg.equals("google")) {
			pkg = "";
		} else {
			pkg = pkg + ".";
		}
		return Logger.getLogger("dasein.cloud.google.std." + pkg + getLastItem(cls.getName()));
	}

	@Nonnull
	public static Logger getWireLogger(@Nonnull Class<?> cls) {
		return Logger.getLogger("dasein.cloud.google.wire." + getLastItem(cls.getPackage().getName()) + "." + getLastItem(cls.getName()));
	}

	public Google() {
	}

	@Override
	public void connect(@Nonnull ProviderContext context, @Nullable CloudProvider computeProvider) {
		super.connect(context, computeProvider);
		initializeGoogleCompute(context);
	}

	public boolean isInitialized() {
		return getContext() != null && getGoogleCompute() != null;
	}

	/**
	 * Initializes google compute engine root service
	 *
	 * @param context provider context
	 */
	protected void initializeGoogleCompute(@Nonnull ProviderContext context) {
		// authorization
		Credential credential = GoogleAuthUtils.authorizeServiceAccount(context.getAccessPublic(), context.getAccessPrivate());

		// create compute engine object
		googleCompute = new Compute.Builder(HttpTransportFactory.getDefaultInstance(), JacksonFactory.getDefaultInstance(), null)
				.setApplicationName(GCE_DASIN_APPLICATION_NAME)
				.setHttpRequestInitializer(credential)
				.build();
	}

	@Override
	@Nonnull
	public String getCloudName() {
		ProviderContext ctx = getContext();
		String name = (ctx == null ? null : ctx.getCloudName());

		return (name == null ? "Google" : name);
	}

	@Override
	@Nonnull
	public GoogleDataCenters getDataCenterServices() {
		return new GoogleDataCenters(this);
	}

	@Override
	public GoogleCompute getComputeServices() {
		return new GoogleCompute(this);
	}

	@Override
	public GoogleNetwork getNetworkServices() {
		return new GoogleNetwork(this);
	}

	@Override
	@Nonnull
	public String getProviderName() {
		ProviderContext ctx = getContext();
		String name = (ctx == null ? null : ctx.getProviderName());

		return (name == null ? "Google" : name);
	}

	@Override
	@Nullable
	public String testContext() {
		if (logger.isTraceEnabled()) {
			logger.trace("ENTER - " + Google.class.getName() + ".testContext()");
		}
		try {
			ProviderContext ctx = getContext();

			if (ctx == null) {
				logger.warn("No context was provided for testing");
				return null;
			}
			try {
				// TODO: Go to Google and verify that the specified credentials in the context are correct
				// return null if they are not
				// return an account number if they are
				return null;
			} catch (Throwable t) {
				logger.error("Error querying API key: " + t.getMessage());
				t.printStackTrace();
				return null;
			}
		} finally {
			if (logger.isTraceEnabled()) {
				logger.trace("EXIT - " + Google.class.getName() + ".textContext()");
			}
		}
	}
}