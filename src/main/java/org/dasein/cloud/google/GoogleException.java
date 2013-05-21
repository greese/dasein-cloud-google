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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 * Base exception class for errors that occur in Google.
 * <p>Created by George Reese: 12/06/2012 9:44 AM</p>
 * @author George Reese
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleException extends CloudException {
	static private final Logger wire = Google.getWireLogger(GoogleException.class);
	static private final Logger logger = Google.getLogger(GoogleException.class);

	static public class ParsedException {
		public int code;
		public String message;
		public String providerCode;
		public CloudErrorType type;
		public ParsedException(@Nonnull HttpResponse response) {
			code = response.getStatusLine().getStatusCode();
			providerCode = toCode(code);
			message = "";
			type = CloudErrorType.GENERAL;

			try {
				HttpEntity entity = response.getEntity();

				if( entity != null ) {
					String json = EntityUtils.toString(entity);

					if( wire.isDebugEnabled() ) {
						wire.debug(json);
					}
					message = json;
					try {
						JSONObject ob = new JSONObject(json);

						if( ob.has("list") ) {
							JSONArray list = ob.getJSONArray("list");

							if( list != null && list.length() > 0 ) {
								JSONObject error = list.getJSONObject(0);

								if( error.has("message") ) {
									message = error.getString("message");
								}
								if( error.has("errorcode") ) {
									providerCode = error.getString("errorcode");
								}
							}
						}
					}
					catch( JSONException ignore ) {
						// ignore parsing errors, probably html or xml
					}
				}
			}
			catch( Throwable e ) {
				logger.error("Failed to parse error from GoGrid: " + e.getMessage());
			}
		}

		private @Nonnull String toCode(int code) {
			switch( code ) {
			case 400: return "IllegalArgument";
			case 401: return "Unauthorized";
			case 403: return "AuthenticationFailed";
			case 404: return "NotFound";
			case 500: return "UnexpectedError";
			}
			return String.valueOf(code);
		}
	}
	public GoogleException(@Nonnull Throwable cause) {
		super(cause);
	}

	public GoogleException(@Nonnull CloudErrorType type, @Nonnegative int httpCode, @Nonnull String providerCode, @Nonnull String message) {
		super(type, httpCode, providerCode, message);
	}

	public GoogleException(ParsedException exception) {
		super(exception.type, exception.code, exception.providerCode, exception.message);
	}
}
