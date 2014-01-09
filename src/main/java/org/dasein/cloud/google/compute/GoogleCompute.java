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

package org.dasein.cloud.google.compute;

import org.dasein.cloud.compute.AbstractComputeServices;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.*;

import javax.annotation.Nonnull;

/**
 * Implements the compute services supported in the Google API.
 *
 * @since 2013.01
 */
public class GoogleCompute extends AbstractComputeServices {
	private Google provider;

	public GoogleCompute(Google provider) {
		this.provider = provider;
	}

	@Nonnull
	public GoogleServerSupport getVirtualMachineSupport() {
		return new GoogleServerSupport(provider);
	}

	@Nonnull
	public GoogleDiskSupport getVolumeSupport() {
		return new GoogleDiskSupport(provider);
	}

	@Nonnull
	public GoogleSnapshotSupport getSnapshotSupport() {
		return new GoogleSnapshotSupport(provider);
	}

	@Nonnull
	public GoogleImageSupport getImageSupport() {
		return new GoogleImageSupport(provider);
	}

	/**
	 * Additional service for google operations management
	 *
	 * @return	operation support service
	 */
	@Nonnull
	public OperationSupport getOperationsSupport() {
		return new GoogleOperationSupport(provider);
	}

}
