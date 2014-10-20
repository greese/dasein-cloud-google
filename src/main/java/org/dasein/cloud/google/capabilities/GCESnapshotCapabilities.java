/**
 * Copyright (C) 2012-2014 Dell, Inc
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

package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.*;
import org.dasein.cloud.compute.SnapshotCapabilities;
import org.dasein.cloud.google.Google;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

public class GCESnapshotCapabilities extends AbstractCapabilities<Google> implements SnapshotCapabilities{
    public GCESnapshotCapabilities(@Nonnull Google cloud){super(cloud);}

    @Nonnull
    @Override
    public String getProviderTermForSnapshot(@Nonnull Locale locale){
        return "snapshot";
    }

    @Nullable
    @Override
    public VisibleScope getSnapshotVisibleScope() {
        return null;
    }

    @Nonnull
    @Override
    public Requirement identifyAttachmentRequirement() throws InternalException, CloudException{
        return Requirement.OPTIONAL;
    }

    @Override
    public boolean supportsSnapshotCopying() throws CloudException, InternalException{
        return false;
    }

    @Override
    public boolean supportsSnapshotCreation() throws CloudException, InternalException{
        return true;
    }

    @Override
    public boolean supportsSnapshotSharing() throws InternalException, CloudException{
        return false;
    }

    @Override
    public boolean supportsSnapshotSharingWithPublic() throws InternalException, CloudException{
        return false;
    }
}
