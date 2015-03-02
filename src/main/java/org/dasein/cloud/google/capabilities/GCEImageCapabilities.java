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
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GCEImageCapabilities extends AbstractCapabilities<Google> implements ImageCapabilities {
    public GCEImageCapabilities( @Nonnull Google cloud ) {
        super(cloud);
    }

    @Override
    public boolean canBundle( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canImage( @Nonnull VmState fromState ) throws CloudException, InternalException {
        return true;
    }

    @Override
    public @Nonnull String getProviderTermForImage( @Nonnull Locale locale, @Nonnull ImageClass cls ) {
        return "image";
    }

    @Override
    public @Nonnull String getProviderTermForCustomImage( @Nonnull Locale locale, @Nonnull ImageClass cls ) {
        return "image";
    }

    @Override
    public @Nullable VisibleScope getImageVisibleScope() {
        return VisibleScope.ACCOUNT_GLOBAL;
    }

    @Override
    public @Nonnull Requirement identifyLocalBundlingRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Iterable<MachineImageFormat> listSupportedFormats() throws CloudException, InternalException {
        return Collections.singletonList(MachineImageFormat.RAW);
    }

    @Override
    public @Nonnull Iterable<MachineImageFormat> listSupportedFormatsForBundling() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<ImageClass> listSupportedImageClasses() throws CloudException, InternalException {
        return Collections.unmodifiableList(Collections.singletonList(ImageClass.MACHINE));
    }

    @Override
    public @Nonnull Iterable<MachineImageType> listSupportedImageTypes() throws CloudException, InternalException {
        return Collections.unmodifiableList(Collections.singletonList(MachineImageType.STORAGE));
    }

    @Override
    public boolean supportsDirectImageUpload() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsImageCapture( @Nonnull MachineImageType type ) throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsImageSharing() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsImageSharingWithPublic() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsPublicLibrary( @Nonnull ImageClass cls ) throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsImageCopy() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsListingAllRegions() throws CloudException, InternalException {
        return true;
    }
}
