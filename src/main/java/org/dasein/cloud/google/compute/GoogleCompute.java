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

import javax.annotation.Nonnull;

import org.dasein.cloud.compute.AbstractComputeServices;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.compute.server.GoogleDiskSupport;
import org.dasein.cloud.google.compute.server.GoogleImageSupport;
import org.dasein.cloud.google.compute.server.GoogleServerSupport;
import org.dasein.cloud.google.compute.server.GoogleSnapshotSupport;

/**
 * Implements the compute services supported in the Google API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleCompute extends AbstractComputeServices {
	private Google provider;

    public GoogleCompute(Google provider) {
		this.provider = provider;
	}

    public @Nonnull GoogleServerSupport getVirtualMachineSupport() {
        return new GoogleServerSupport(provider);
    }

    public @Nonnull GoogleDiskSupport getVolumeSupport() {
        return new GoogleDiskSupport(provider);
    }

    public @Nonnull GoogleSnapshotSupport getSnapshotSupport() {
        return new GoogleSnapshotSupport(provider);
    }

    public @Nonnull GoogleImageSupport getImageSupport() {
        return new GoogleImageSupport(provider);
    }
}
