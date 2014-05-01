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

package org.dasein.cloud.google.common;

import com.google.api.client.http.HttpStatusCodes;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;

/**
 * Simple error representing a failure to set up a configuration. <p>Created by George Reese: 12/06/2012 9:44 AM</p>
 *
 * @author George Reese
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class NoContextException extends CloudException {
	private static final long serialVersionUID = 3011536400976881479L;

	public NoContextException() {
		super(CloudErrorType.GENERAL, HttpStatusCodes.STATUS_CODE_FORBIDDEN, "none", "Forbidden as no context was set for this request");
	}
}

