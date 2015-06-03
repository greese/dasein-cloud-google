/**
 * Copyright (C) 2012-2015 Dell, Inc
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

package org.dasein.cloud.google.storage;

import org.dasein.cloud.google.Google;
import org.dasein.cloud.storage.AbstractStorageServices;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides access to Google Drive storage
 * @author Drew Lyall
 * @version 2014.03
 * @since 2014.03
 */
public class GoogleDrive extends AbstractStorageServices<Google> {

    public GoogleDrive(Google provider) {
        super(provider);
    }

    @Override
    public @Nullable DriveSupport getOnlineStorageSupport() {
        return new DriveSupport(getProvider());
    }
}
