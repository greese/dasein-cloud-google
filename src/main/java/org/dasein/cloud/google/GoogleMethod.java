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

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * Represents the interaction point between Dasein Cloud and the underlying REST API.
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class GoogleMethod {
    static public final int OK        = 200;
    static public final int CREATED   = 201;
    static public final int ACCEPTED  = 202;
    static public final int NOT_FOUND = 404;

	static public class Param {
		private String key;
		private String value;

		public Param(@Nonnull String key, @Nullable String value) throws InternalException {
			this.key = key;
			try {
        if ( value != null ) {
				  this.value = URLEncoder.encode(value, "utf-8");
        }
			}
			catch( UnsupportedEncodingException e ) {
				logger.error("UTF-8 unsupported: " + e.getMessage());
				e.printStackTrace();
				throw new InternalException(e);
			}
		}

		public @Nonnull String getKey() {
			return key;
		}

		public @Nonnull String getValue() {
			return value;
		}
	}

	static public final String VOLUME 			= "/disks";
	static public final String FIREWALL 		= "/global/firewalls";
	static public final String IMAGE 			= "/global/images";
	static public final String SERVER 			= "/instances";
	static public final String KERNEL 			= "/global/kernels";
	static public final String MACHINE_TYPE 	= "/machineTypes";
	static public final String NETWORK 			= "/global/networks";
	static public final String SNAPSHOT 		= "/global/snapshots";
	static public final String ZONE 			= "/zones";
	static public final String GLOBAL_OPERATION = "/global/operations";
	static public final String OPERATION 		= "/operations";

	static public final String VERSION 			= "v1beta15";

	static private final Logger logger = Google.getLogger(GoogleMethod.class);
	static private final Logger wire = Google.getWireLogger(GoogleMethod.class);

	private Google provider;

	public GoogleMethod(@Nonnull Google provider) { this.provider = provider; }

	public @Nullable JSONArray get(@Nonnull String service, @Nullable Param ... params) throws CloudException, InternalException {
		if( logger.isTraceEnabled() ) {
			logger.trace("ENTER - " + Google.class.getName() + ".get(" + service + "," + Arrays.toString(params) + ")");
		}
		if( wire.isDebugEnabled() ) {
			wire.debug("");
			wire.debug(">>> [GET (" + (new Date()) + ")] -> " + service + " >--------------------------------------------------------------------------------------");
		}
		try {
			ProviderContext ctx = provider.getContext();

			if( ctx == null ) {
				throw new CloudException("No context was set for this request");
			}

			String endpoint = getEndpoint(ctx, service);

			if( logger.isDebugEnabled() ) {
				logger.debug("endpoint=" + endpoint);
				logger.debug("");
				logger.debug("Getting accessToken from cache");
			}

			Cache<String> cache = Cache.getInstance(provider, "accessToken", String.class, CacheLevel.CLOUD, new TimePeriod<Hour>(1, TimePeriod.HOUR));
			Collection<String> accessToken = (Collection<String>)cache.get(ctx);

			if (accessToken == null) {
				if( logger.isDebugEnabled() ) {
					logger.debug("Getting the accessToken afresh");
				}
				accessToken = new ArrayList<String>();
				accessToken.add(getAccessToken(ctx));
				cache.put(ctx, accessToken);
			}

			//TODO: Logging access token is not good
			if( logger.isDebugEnabled() ) {
				logger.debug("accessToken=" + accessToken);
			}

			String paramString = "?access_token=" + accessToken.iterator().next() + "&token_type=Bearer&expires_in=3600";


			if( params != null && params.length > 0 ) {
				for( Param p : params ) {
          if ( p.getValue() != null ) {
					  paramString = paramString + "&" + p.getKey() + "=" + p.getValue();
          }
				}
			}
			if( logger.isDebugEnabled() ) {
				logger.debug("Param string=" + paramString);
			}
			HttpGet get = new HttpGet(endpoint + paramString);
			HttpClient client = getClient(ctx, endpoint.startsWith("https"));

			if( wire.isDebugEnabled() ) {
				wire.debug(get.getRequestLine().toString());
				for( Header header : get.getAllHeaders() ) {
					wire.debug(header.getName() + ": " + header.getValue());
				}
				wire.debug("");
			}
			HttpResponse response;

			try {
				response = client.execute(get);
				if( wire.isDebugEnabled() ) {
					wire.debug(response.getStatusLine().toString());
				}
			}
			catch( IOException e ) {
				logger.error("I/O error from server communications: " + e.getMessage());
				e.printStackTrace();
				throw new InternalException(e);
			}
			int status = response.getStatusLine().getStatusCode();

			if( status == NOT_FOUND ) {
				return null;
			}
			if( status == OK ) {
				HttpEntity entity = response.getEntity();
				String json;

				if( entity == null ) {
					return null;
				}
				try {
					json = EntityUtils.toString(entity);
					if( wire.isDebugEnabled() ) {
						wire.debug(json);
					}
				}
				catch( IOException e ) {
					logger.error("Failed to read JSON entity");
					e.printStackTrace();
					throw new CloudException(e);
				}
				try {
					JSONObject r = new JSONObject(json);

					if (r.has("items")) 
						return r.getJSONArray("items");
					else if (r.has("name")){
						JSONArray array = new JSONArray();
						array.put(r);
						return array;
					} else return null;
				}
				catch( JSONException e ) {
					logger.error("Invalid JSON from cloud: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}
			}
			else if( status == NOT_FOUND ) {
				return null;
			}
			else if( status == 400 && service.endsWith("get") ) {
				return null;
			}
			throw new GoogleException(new GoogleException.ParsedException(response));
		}
		finally {
			if( logger.isTraceEnabled() ) {
				logger.trace("EXIT - " + GoogleMethod.class.getName() + ".get()");
			}
			if( wire.isDebugEnabled() ) {
				wire.debug("<<< [GET (" + (new Date()) + ")] -> " + service + " <--------------------------------------------------------------------------------------");
				wire.debug("");
			}
		}
	}


	public @Nullable JSONObject post(@Nonnull String service, @Nonnull JSONObject jsonPayload) throws CloudException, InternalException {
		if( logger.isTraceEnabled() ) {
			logger.trace("ENTER - " + Google.class.getName() + ".post(" + service + ")");
		}
		if( wire.isDebugEnabled() ) {
			wire.debug("");
			wire.debug(">>> [POST (" + (new Date()) + ")] -> " + service + " >--------------------------------------------------------------------------------------");
		}
		try {
			ProviderContext ctx = provider.getContext();

			if( ctx == null ) {
				throw new CloudException("No context was set for this request");
			}

			String endpoint = getEndpoint(ctx, service);

			if( logger.isDebugEnabled() ) {
				logger.debug("endpoint=" + endpoint);
				logger.debug("");
				logger.debug("Getting accessToken from cache");
			}

			Cache<String> cache = Cache.getInstance(provider, "accessToken", String.class, CacheLevel.CLOUD, new TimePeriod<Hour>(1, TimePeriod.HOUR));
			Collection<String> accessToken = (Collection<String>)cache.get(ctx);

			if (accessToken == null) {
				if( logger.isDebugEnabled() ) {
					logger.debug("Getting the accessToken afresh");
				}
				accessToken = new ArrayList<String>();
				accessToken.add(getAccessToken(ctx));
				cache.put(ctx, accessToken);
			}

			//TODO: Logging access token is not good
			if( logger.isDebugEnabled() ) {
				logger.debug("accessToken=" + accessToken);
			}

			String paramString = "?access_token=" + accessToken.iterator().next() + "&token_type=Bearer&expires_in=3600";

			if( logger.isDebugEnabled() ) {
				logger.debug("Param string=" + paramString);
			}
			HttpPost post = new HttpPost(endpoint + paramString);
			HttpClient client = getClient(ctx, endpoint.startsWith("https"));

			StringEntity stringEntity;
			try {
				stringEntity = new StringEntity(jsonPayload.toString());
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				return null;
			}
			stringEntity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
			post.setEntity(stringEntity);


			if( wire.isDebugEnabled() ) {
				wire.debug(post.getRequestLine().toString());
				for( Header header : post.getAllHeaders() ) {
					wire.debug(header.getName() + ": " + header.getValue());
				}
				wire.debug("");
			}
			HttpResponse response;

			try {
				response = client.execute(post);
				if( wire.isDebugEnabled() ) {
					wire.debug(response.getStatusLine().toString());
				}
			}
			catch( IOException e ) {
				logger.error("I/O error from server communications: " + e.getMessage());
				e.printStackTrace();
				throw new InternalException(e);
			}
			int status = response.getStatusLine().getStatusCode();

			if( status == NOT_FOUND ) {
				return null;
			}
			if( status == OK || status == ACCEPTED) {
				HttpEntity entity = response.getEntity();
				String json;

				if( entity == null ) {
					return null;
				}
				try {
					json = EntityUtils.toString(entity);
					if( wire.isDebugEnabled() ) {
						wire.debug(json);
					}
				}
				catch( IOException e ) {
					logger.error("Failed to read JSON entity");
					e.printStackTrace();
					throw new CloudException(e);
				}
				try {
					return new JSONObject(json);
				}
				catch( JSONException e ) {
					logger.error("Invalid JSON from cloud: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}
			}
			else if( status == NOT_FOUND ) {
				return null;
			}
			else if( status == 400 && service.endsWith("post") ) {
				return null;
			}
			throw new GoogleException(new GoogleException.ParsedException(response));
		}
		finally {
			if( logger.isTraceEnabled() ) {
				logger.trace("EXIT - " + GoogleMethod.class.getName() + ".post()");
			}
			if( wire.isDebugEnabled() ) {
				wire.debug("<<< [POST (" + (new Date()) + ")] -> " + service + " <--------------------------------------------------------------------------------------");
				wire.debug("");
			}
		}
	}

	public @Nullable JSONObject patch(@Nonnull String service, @Nonnull JSONObject jsonPayload) throws CloudException, InternalException {
		if( logger.isTraceEnabled() ) {
			logger.trace("ENTER - " + Google.class.getName() + ".patch(" + service + ")");
		}
		if( wire.isDebugEnabled() ) {
			wire.debug("");
			wire.debug(">>> [PATCH (" + (new Date()) + ")] -> " + service + " >--------------------------------------------------------------------------------------");
		}
		try {
			ProviderContext ctx = provider.getContext();

			if( ctx == null ) {
				throw new CloudException("No context was set for this request");
			}

			String endpoint = getEndpoint(ctx, service);

			if( logger.isDebugEnabled() ) {
				logger.debug("endpoint=" + endpoint);
				logger.debug("");
				logger.debug("Getting accessToken from cache");
			}

			Cache<String> cache = Cache.getInstance(provider, "accessToken", String.class, CacheLevel.CLOUD, new TimePeriod<Hour>(1, TimePeriod.HOUR));
			Collection<String> accessToken = (Collection<String>)cache.get(ctx);

			if (accessToken == null) {
				if( logger.isDebugEnabled() ) {
					logger.debug("Getting the accessToken afresh");
				}
				accessToken = new ArrayList<String>();
				accessToken.add(getAccessToken(ctx));
				cache.put(ctx, accessToken);
			}

			//TODO: Logging access token is not good
			if( logger.isDebugEnabled() ) {
				logger.debug("accessToken=" + accessToken);
			}

			String paramString = "?access_token=" + accessToken.iterator().next() + "&token_type=Bearer&expires_in=3600";

			if( logger.isDebugEnabled() ) {
				logger.debug("Param string=" + paramString);
			}
			HttpPatch patch = new HttpPatch(endpoint + paramString);
			HttpClient client = getClient(ctx, endpoint.startsWith("https"));

			StringEntity stringEntity;
			try {
				stringEntity = new StringEntity(jsonPayload.toString());
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				return null;
			}
			stringEntity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
			patch.setEntity(stringEntity);

			if( wire.isDebugEnabled() ) {
				wire.debug(patch.getRequestLine().toString());
				for( Header header : patch.getAllHeaders() ) {
					wire.debug(header.getName() + ": " + header.getValue());
				}
				wire.debug("");
			}
			HttpResponse response;

			try {
				response = client.execute(patch);
				if( wire.isDebugEnabled() ) {
					wire.debug(response.getStatusLine().toString());
				}
			}
			catch( IOException e ) {
				logger.error("I/O error from server communications: " + e.getMessage());
				e.printStackTrace();
				throw new InternalException(e);
			}
			int status = response.getStatusLine().getStatusCode();

			if( status == NOT_FOUND ) {
				return null;
			}
			if( status == OK || status == ACCEPTED) {
				HttpEntity entity = response.getEntity();
				String json;

				if( entity == null ) {
					return null;
				}
				try {
					json = EntityUtils.toString(entity);
					if( wire.isDebugEnabled() ) {
						wire.debug(json);
					}
				}
				catch( IOException e ) {
					logger.error("Failed to read JSON entity");
					e.printStackTrace();
					throw new CloudException(e);
				}
				try {
					return new JSONObject(json);
				}
				catch( JSONException e ) {
					logger.error("Invalid JSON from cloud: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}
			}
			else if( status == NOT_FOUND ) {
				return null;
			}
			else if( status == 400 && service.endsWith("patch") ) {
				return null;
			}
			throw new GoogleException(new GoogleException.ParsedException(response));
		}
		finally {
			if( logger.isTraceEnabled() ) {
				logger.trace("EXIT - " + GoogleMethod.class.getName() + ".patch()");
			}
			if( wire.isDebugEnabled() ) {
				wire.debug("<<< [PATCH (" + (new Date()) + ")] -> " + service + " <--------------------------------------------------------------------------------------");
				wire.debug("");
			}
		}
	}

	public @Nullable JSONObject delete(@Nonnull String service, @Nullable Param ... params) throws CloudException, InternalException {
		if( logger.isTraceEnabled() ) {
			logger.trace("ENTER - " + Google.class.getName() + ".delete(" + service + "," + Arrays.toString(params) + ")");
		}
		if( wire.isDebugEnabled() ) {
			wire.debug("");
			wire.debug(">>> [DELETE (" + (new Date()) + ")] -> " + service + " >--------------------------------------------------------------------------------------");
		}
		try {
			ProviderContext ctx = provider.getContext();

			if( ctx == null ) {
				throw new CloudException("No context was set for this request");
			}

			String endpoint = getEndpoint(ctx, service);

			if( logger.isDebugEnabled() ) {
				logger.debug("endpoint=" + endpoint);
				logger.debug("");
				logger.debug("Getting accessToken from cache");
			}

			Cache<String> cache = Cache.getInstance(provider, "accessToken", String.class, CacheLevel.CLOUD, new TimePeriod<Hour>(1, TimePeriod.HOUR));
			Collection<String> accessToken = (Collection<String>)cache.get(ctx);

			if (accessToken == null) {
				if( logger.isDebugEnabled() ) {
					logger.debug("Getting the accessToken afresh");
				}
				accessToken = new ArrayList<String>();
				accessToken.add(getAccessToken(ctx));
				cache.put(ctx, accessToken);
			}

			//TODO: Logging access token is not good
			if( logger.isDebugEnabled() ) {
				logger.debug("accessToken=" + accessToken);
			}

			String paramString = "?access_token=" + accessToken.iterator().next() + "&token_type=Bearer&expires_in=3600";

			String id = "";

			if( params != null && params.length > 0 ) {
				for( Param p : params ) {
                	if (p.getKey().equals("id")) id = p.getValue();
                }

			}
			if( logger.isDebugEnabled() ) {
				logger.debug("Param string=" + paramString);
			}

			HttpDelete delete = new HttpDelete(endpoint + "/" + id + paramString);
			HttpClient client = getClient(ctx, endpoint.startsWith("https"));

			if( wire.isDebugEnabled() ) {
				wire.debug(delete.getRequestLine().toString());
				for( Header header : delete.getAllHeaders() ) {
					wire.debug(header.getName() + ": " + header.getValue());
				}
				wire.debug("");
			}
			HttpResponse response;

			try {
				response = client.execute(delete);
				if( wire.isDebugEnabled() ) {
					wire.debug(response.getStatusLine().toString());
				}
			}
			catch( IOException e ) {
				logger.error("I/O error from server communications: " + e.getMessage());
				e.printStackTrace();
				throw new InternalException(e);
			}
			int status = response.getStatusLine().getStatusCode();

			if( status == NOT_FOUND ) {
				return null;
			}
			if( status == OK || status == ACCEPTED) {
				HttpEntity entity = response.getEntity();
				String json;

				if( entity == null ) {
					return null;
				}
				try {
					json = EntityUtils.toString(entity);
					if( wire.isDebugEnabled() ) {
						wire.debug(json);
					}
				}
				catch( IOException e ) {
					logger.error("Failed to read JSON entity");
					e.printStackTrace();
					throw new CloudException(e);
				}
				try {
					return new JSONObject(json);
				}
				catch( JSONException e ) {
					logger.error("Invalid JSON from cloud: " + e.getMessage());
					e.printStackTrace();
					throw new CloudException(e);
				}
			}
			else if( status == NOT_FOUND ) {
				return null;
			}
			else if( status == 400 && service.endsWith("delete") ) {
				return null;
			}
			throw new GoogleException(new GoogleException.ParsedException(response));
		}
		finally {
			if( logger.isTraceEnabled() ) {
				logger.trace("EXIT - " + GoogleMethod.class.getName() + ".delete()");
			}
			if( wire.isDebugEnabled() ) {
				wire.debug("<<< [DELETE (" + (new Date()) + ")] -> " + service + " <--------------------------------------------------------------------------------------");
				wire.debug("");
			}
		}
	}


	public @Nonnull String getProjectId(@Nonnull ProviderContext ctx) {
		return ctx.getAccountNumber().toString();
	}

	public static @Nonnull String getIss(@Nonnull ProviderContext ctx) {
		return System.getProperty("apiSharedKey").toString();
	}

	public static @Nonnull String getPrivateKey(@Nonnull ProviderContext ctx) {
		return  System.getProperty("apiSecretKey").toString();
	}

	//TODO: rewrite/review the module
	static @Nonnull String getToken(@Nonnull String iss, @Nonnull String p12File) throws CloudException {
		if( logger.isDebugEnabled() ) {
			logger.debug("iss: " + iss);
			logger.debug("p12File: " + p12File);
		}

		String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
		StringBuffer token = new StringBuffer();

		try {
			token.append(Base64.encodeBase64URLSafeString(header.getBytes("UTF-8")));

			token.append(".");

			String scope = "https://www.googleapis.com/auth/compute";
			String aud = "https://accounts.google.com/o/oauth2/token";
			String expiry = Long.toString( ( System.currentTimeMillis()/1000 ) + 3600);
			String startTime = Long.toString( ( System.currentTimeMillis()/1000 ));
			
			String payload = "{\"iss\": \""+ iss +"\", \"scope\": \""+ scope +"\", \"aud\": \""+ aud +"\", \"exp\": \""+ expiry +"\", \"iat\": \""+ startTime +"\"}";

			token.append(Base64.encodeBase64URLSafeString(payload.getBytes("UTF-8")));

			// TODO: the password is hardcoded. This has to be read from the ctx or from the environment variable
			char[] password = "notasecret".toCharArray();
			FileInputStream iStream = new FileInputStream(new File(p12File));
			KeyStore store = KeyStore.getInstance("PKCS12");
			try {
				store.load(iStream, password);                
			} 
			finally {
				try {
					iStream.close();
				} catch (IOException e) {
					e.printStackTrace();
					logger.error("Could not read the keystore file");
					throw new CloudException(e);
				}
			}
			String alias = "";

			Enumeration<String> aliases = store.aliases();
			while(aliases.hasMoreElements()) {
				String keyStoreAlias = aliases.nextElement().toString();
				if(store.isKeyEntry(keyStoreAlias)) {
					alias  = keyStoreAlias;
					break;
				}   
			}
			
			PrivateKey  privateKey = (PrivateKey) store.getKey(alias, password);
 
			Signature shaSignature = Signature.getInstance("SHA256withRSA");
			shaSignature.initSign(privateKey);
			shaSignature.update(token.toString().getBytes("UTF-8"));
			String signedToken = Base64.encodeBase64URLSafeString(shaSignature.sign());

			//Separate with a period
			token.append(".");

			//Add the encoded signature
			token.append(signedToken);
			return token.toString();

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Could not sign the payload with the private key");
			throw new CloudException(e);
		}
	}

	public static @Nonnull String getAccessToken(@Nonnull ProviderContext ctx) throws CloudException {
		if( logger.isTraceEnabled() ) {
			logger.trace("ENTER - " + Google.class.getName() + ".getAccessToken()");
		}
		if( wire.isDebugEnabled() ) {
			wire.debug("");
			wire.debug(">>> [GET ACCESS TOKEN (" + (new Date()) + ")] >--------------------------------------------------------------------------------------");
		}
		String iss = getIss(ctx);
		String p12File = getPrivateKey(ctx) ;
		String authUrl = "https://accounts.google.com/o/oauth2/token";

		try {
			HttpClient client = new DefaultHttpClient();
			List<BasicNameValuePair> formparams = new ArrayList<BasicNameValuePair>();
			formparams.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"));
			formparams.add(new BasicNameValuePair("assertion", getToken(iss, p12File)));
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");

			HttpPost httppost = new HttpPost(authUrl);
			httppost.addHeader("Content-Type", "application/x-www-form-urlencoded");
			httppost.setEntity(entity);

			// TODO: Logging header may not be good
			if( wire.isDebugEnabled() ) {
				wire.debug(httppost.getRequestLine().toString());
				for( Header header : httppost.getAllHeaders() ) {
					wire.debug(header.getName() + ": " + header.getValue());
				}
				wire.debug("");
			}

			HttpResponse httpResponse = client.execute(httppost);

			if( wire.isDebugEnabled() ) {
				wire.debug(httpResponse.getStatusLine().toString());
			}

			int statusCode = httpResponse.getStatusLine().getStatusCode();
			if (statusCode == HttpStatus.SC_OK ) {

				InputStream iStream = httpResponse.getEntity().getContent();
				InputStreamReader iStreamReader = new InputStreamReader(iStream);
				StringBuilder sBuilder = new StringBuilder();
				BufferedReader bufferReader = new BufferedReader(iStreamReader);
				String read = bufferReader.readLine();

				while(read != null) {
					sBuilder.append(read);
					read = bufferReader.readLine();
				}

				JSONObject json = new JSONObject(sBuilder.toString());
				return json.getString("access_token");
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error while parsing the access token");
			throw new CloudException(e);
		} finally {
			if( logger.isTraceEnabled() ) {
				logger.trace("EXIT - " + GoogleMethod.class.getName() + ".getAccessToken()");
			}
			if( wire.isDebugEnabled() ) {
				wire.debug("<<< [GET ACCESS TOKEN (" + (new Date()) + ")] <--------------------------------------------------------------------------------------");
				wire.debug("");
			}
		}
		throw new CloudException("Could not obtain access token");
	}

	private @Nonnull HttpClient getClient(@Nonnull ProviderContext ctx, boolean ssl) {
		HttpParams params = new BasicHttpParams();

		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		//noinspection deprecation
		HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
		HttpProtocolParams.setUserAgent(params, "Dasein Cloud");

		Properties p = ctx.getCustomProperties();

		if( p != null ) {
			String proxyHost = p.getProperty("proxyHost");
			String proxyPort = p.getProperty("proxyPort");

			if( proxyHost != null ) {
				int port = 0;

				if( proxyPort != null && proxyPort.length() > 0 ) {
					port = Integer.parseInt(proxyPort);
				}
				params.setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(proxyHost, port, ssl ? "https" : "http"));
			}
		}
		return new DefaultHttpClient(params);
	}

	public @Nonnull String getEndpoint(@Nonnull ProviderContext ctx, @Nonnull String service) {
		String endpoint = ctx.getEndpoint();

		if( endpoint == null || endpoint.equals("") ) {
			endpoint = "https://www.googleapis.com/compute/"+ GoogleMethod.VERSION +"/projects/";
		}
		if( endpoint.endsWith("/") && service.startsWith("/") ) {
			while( endpoint.endsWith("/") && !endpoint.equals("/") ) {
				endpoint = endpoint.substring(0, endpoint.length()-1);
			}
		}

		if (service.contains(GoogleMethod.IMAGE)) endpoint = endpoint + "/google";
		else if (service.contains("global") || service.equals(GoogleMethod.ZONE)) endpoint = endpoint + "/" + getProjectId(ctx);
		else endpoint = endpoint + "/" + getProjectId(ctx) + "/zones/" + ctx.getRegionId() + "-a" ;

		return endpoint + service;
	}

	
	public static @Nonnull String getResourceName(String resourceLink, String resource) {
		int index = resourceLink.indexOf(resource); 
		String resoureName = resourceLink.substring(index + resource.length() +1 );
		return resoureName ;
	}

	public static void checkError(JSONObject jsonResponse) throws CloudException{
		String errString = null;
		try {
			if (jsonResponse.has("httpErrorStatusCode")) {

				errString = jsonResponse.getString("httpErrorStatusCode") + " " +  jsonResponse.getString("httpErrorMessage");

				if (jsonResponse.has("error")) {
					JSONObject error = jsonResponse.getJSONObject("error");
					if (error.has("errors")) {
						JSONArray errorArray = error.getJSONArray("errors");
						JSONObject errorObject = errorArray.getJSONObject(0);
						if (errorObject.has("message")) errString = errString + " Reason: " + errorObject.getString("message");
					}
				}
			}
		} catch (JSONException e) {
			logger.error("Launches did not come back in the form of a valid list: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}
		if (errString != null) {
			throw new CloudException(errString);
		}
	}

	public @Nonnull String getOperationStatus(String operationUrl, JSONObject response) throws CloudException, InternalException {
		if( logger.isTraceEnabled() ) {
			logger.trace("ENTER - " + Google.class.getName() + ".getOperationStatus(" + operationUrl + ")");
		}
		if( wire.isDebugEnabled() ) {
			wire.debug("");
			wire.debug(">>> [GET OPERATION STATUS (" + (new Date()) + ")] -> " + operationUrl + " >--------------------------------------------------------------------------------------");
		}
		try {
			GoogleMethod method = new GoogleMethod(provider);
			String status = null;
			String operationId = null;

			try {
				if (response != null && response.has("status")) {

					status = (String) response.getString("status");
					if (response.has("name"))
						operationId = response.getString("name");
					if( logger.isDebugEnabled() ) {
						logger.debug("operationId=" + operationId);
					}
				}
			} catch (JSONException e) {
				logger.error("Launches did not come back in the form of a valid list: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
			if (status.equals("DONE")) {
				GoogleMethod.checkError(response);
				return status;
			}

			long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 15L);
			JSONObject operation = null;
			while( timeout > System.currentTimeMillis() ) {
				try { Thread.sleep(150L); }
				catch( InterruptedException ignore ) { }
				if (!status.equals("DONE")) {
					JSONArray jobArray = method.get(operationUrl + "/" + operationId);

					if (jobArray != null) {
						try {
							operation = jobArray.getJSONObject(0);
							status = operation.getString("status");
						} catch (JSONException e) {
							logger.error("Operation did not succeed: " + e.getMessage());
							e.printStackTrace();
							throw new CloudException(e);
						}
					}
					if (status.equals("DONE")) {
						GoogleMethod.checkError(response);
						return status;
					}
				}
			} 
			throw new CloudException("System timed out waiting for Operation to complete");
		} finally {
			if( logger.isTraceEnabled() ) {
				logger.trace("EXIT - " + GoogleMethod.class.getName() + ".getOperationStatus()");
			}
			if( wire.isDebugEnabled() ) {
				wire.debug("<<< [GET OPERATION STATUS (" + (new Date()) + ")] <--------------------------------------------------------------------------------------");
				wire.debug("");
			}
		}
	}
}
