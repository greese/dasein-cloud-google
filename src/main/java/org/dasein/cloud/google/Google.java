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
import com.google.api.services.compute.model.Operation;
import org.dasein.cloud.AbstractCloud;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.google.common.GoogleAuthorizationException;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.compute.GoogleCompute;
import org.dasein.cloud.google.compute.server.OperationSupport;
import org.dasein.cloud.google.network.GoogleNetwork;
import org.dasein.cloud.google.util.GoogleAuthUtils;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.HttpTransportFactory;
import org.dasein.cloud.util.APITrace;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Support for the Google API through Dasein Cloud.
 *
 * <p> Created by George Reese: 12/06/2012 9:35 AM
 *
 * @author George Reese
 * @author igoonich
 * @since 2013.01
 */
public class Google extends AbstractCloud {

	/**
	 * Application name for GCE dasein implementation
	 */
	private static final String GCE_DASIN_APPLICATION_NAME = "Google-Compute-Dasein-Implementation/1.0";

	private static final String GCE_PROVIDER_NAME = "Google";

	/**
	 * Google Compute Engine service locator object
	 */
	private volatile Compute googleCompute;

	/**
	 * Lock for lazy Google Compute Engine initialization
	 */
	private final Object googleComputeLock = new Object();


	public Google() {
	}

	/**
	 * Initializes google compute engine root service
	 *
	 * @return google compute root service
	 * @throws CloudException in case of any errors
	 */
	public Compute getGoogleCompute() throws CloudException {
		// ensure that dasein context is initialized
		if (!isInitialized()) {
			throw new NoContextException();
		}

		// initialization of the google compute service locator
		Compute result = googleCompute;
		if (result == null) {
			synchronized (googleComputeLock) {
				result = googleCompute;
				if (result == null) {
					googleCompute = result = initializeGoogleCompute(getContext());
				}
			}
		}

		return result;
	}

	/**
	 * Initializes google compute engine root service
	 *
	 * @param context provider context
	 * @return google compute root service
	 * @throws GoogleAuthorizationException in case authorization fails
	 */
	protected Compute initializeGoogleCompute(@Nonnull ProviderContext context) throws CloudException {
		// authorization
		Credential credential = GoogleAuthUtils.authorizeServiceAccount(context.getAccessPublic(), context.getAccessPrivate());

		// create compute engine object
		return new Compute.Builder(HttpTransportFactory.getDefaultInstance(), JacksonFactory.getDefaultInstance(), credential)
				.setApplicationName(GCE_DASIN_APPLICATION_NAME)
				.build();
	}

	/**
	 * Check that context is initialized
	 *
	 * @return {@code true} if context is initialized, {@code false} - otherwise
	 */
	public boolean isInitialized() {
		return getContext() != null;
	}

	@Override
	public @Nonnull GoogleDataCenters getDataCenterServices() {
		// TODO: create only once
		return new GoogleDataCenters(this);
	}

	@Override
	public @Nonnull GoogleCompute getComputeServices() {
		// TODO: create only once
		return new GoogleCompute(this);
	}

	@Override
	public @Nonnull GoogleNetwork getNetworkServices() {
		// TODO: create only once
		return new GoogleNetwork(this);
	}

	@Override
	public @Nonnull String getCloudName() {
		ProviderContext ctx = getContext();
		String name = (ctx == null ? null : ctx.getCloudName());
		return (name == null ? GCE_PROVIDER_NAME : name);
	}

	@Override
	public @Nonnull String getProviderName() {
		ProviderContext ctx = getContext();
		String name = (ctx == null ? null : ctx.getProviderName());
		return (name == null ? GCE_PROVIDER_NAME : name);
	}

    /**
     * Submits remote operation to google cloud and waits for its completion
     * @param operation remote operation to perform
     * @param <T> operation
     * @return completed operation
     * @throws CloudException
     * @throws InternalException
     */
    public <T> T submit(CloudUpdateOperation operation) throws CloudException, InternalException {
        APITrace.begin(this, operation.getId());
        try{
            Operation job = operation.createOperation(this);
            return (T) this.getOperationsSupport().waitUntilOperationCompletes(job);
        } catch (IOException e) {
            GoogleExceptionUtils.handleGoogleResponseError(e, false);
        } finally {
            APITrace.end();
        }

        return null;
    }

    /**
     * Fetches remote entity from google cloud
     * @param operation remote fetch operation
     * @param <T> operation
     * @return completed operation
     * @throws CloudException
     * @throws InternalException
     */
    public <T> T fetch(CloudOperation<T> operation) throws CloudException, InternalException {
        APITrace.begin(this, operation.getId());
        try{
            return operation.createOperation(this);
        } catch (IOException e) {
            GoogleExceptionUtils.handleGoogleResponseError(e, false);
        } finally {
            APITrace.end();
        }

        return null;
    }

    /**
     * Returns region from current context
     * @return region id
     */
    public String getRegionId() {
        return getContext().getRegionId();
    }

    /**
     * Returns project for current context
     * @return project id
     */
    public String getProject() {
        return getContext().getAccountNumber();
    }

    public OperationSupport getOperationsSupport() {
        return getComputeServices().getOperationsSupport();
    }
}