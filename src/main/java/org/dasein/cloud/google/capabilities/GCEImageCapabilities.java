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

package org.dasein.cloud.google.capabilities;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class GCEImageCapabilities extends AbstractCapabilities<Google> implements ImageCapabilities{
    public GCEImageCapabilities(@Nonnull Google cloud){super(cloud);}

    @Override public boolean canBundle(@Nonnull VmState fromState) throws CloudException, InternalException{
        return false;
    }

    @Override public boolean canImage(@Nonnull VmState fromState) throws CloudException, InternalException{
        return false;
    }

    @Nonnull @Override public String getProviderTermForImage(@Nonnull Locale locale, @Nonnull ImageClass cls){
        return "image";
    }

    @Nonnull @Override public String getProviderTermForCustomImage(@Nonnull Locale locale, @Nonnull ImageClass cls){
        return "image";
    }

    @Nonnull @Override public Requirement identifyLocalBundlingRequirement() throws CloudException, InternalException{
        return Requirement.NONE;
    }

    @Nonnull @Override public Iterable<MachineImageFormat> listSupportedFormats() throws CloudException, InternalException{
        return Collections.singletonList(MachineImageFormat.RAW);
    }

    @Nonnull @Override public Iterable<MachineImageFormat> listSupportedFormatsForBundling() throws CloudException, InternalException{
        return Collections.emptyList();
    }

    @Nonnull @Override public Iterable<ImageClass> listSupportedImageClasses() throws CloudException, InternalException{
        ArrayList<ImageClass> tmp = new ArrayList<ImageClass>();
        tmp.add(ImageClass.MACHINE);
        return tmp;
    }

    @Nonnull @Override public Iterable<MachineImageType> listSupportedImageTypes() throws CloudException, InternalException{
        ArrayList<MachineImageType> imageType = new ArrayList<MachineImageType>();
        imageType.add(MachineImageType.STORAGE);
        return imageType;
    }

    @Override public boolean supportsDirectImageUpload() throws CloudException, InternalException{
        return false;
    }

    @Override public boolean supportsImageCapture(@Nonnull MachineImageType type) throws CloudException, InternalException{
        return false;
    }

    @Override public boolean supportsImageSharing() throws CloudException, InternalException{
        return true;
    }

    @Override public boolean supportsImageSharingWithPublic() throws CloudException, InternalException{
        return true;
    }

    @Override public boolean supportsPublicLibrary(@Nonnull ImageClass cls) throws CloudException, InternalException{
        return true;
    }
}
