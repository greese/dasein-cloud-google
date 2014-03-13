package org.dasein.cloud.google.util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.SecurityUtils;
import com.google.common.collect.ImmutableSet;
import org.dasein.cloud.google.common.GoogleAuthorizationException;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;

import static com.google.api.client.util.Preconditions.checkArgument;
import static com.google.api.client.util.Preconditions.checkNotNull;

/**
 * OAuth authentication utils
 *
 * @author igoonich
 * @since 10.12.2013
 */
public final class GoogleAuthUtils {

	private static final Collection<String> READ_WRITE_GCE_SCOPE = ImmutableSet.of("https://www.googleapis.com/auth/compute");

	private GoogleAuthUtils() {
		throw new AssertionError();
	}

	/**
	 * Authorize service account using PEM encoded private key and service account ID as {@code byte[]}
	 *
	 * @param accountIdBytes              service account ID as byte array
	 * @param pemServiceAccountPrivateKey private key byte array in PEM format
	 * @return authorized {@link com.google.api.client.auth.oauth2.Credential} wrapper object
	 * @throws GoogleAuthorizationException if service account cannot be authorized
	 */
	public static Credential authorizeServiceAccount(byte[] accountIdBytes, byte[] pemServiceAccountPrivateKey) throws GoogleAuthorizationException {
		try {
			return authorizeServiceAccount(new String(accountIdBytes, "UTF-8"), pemServiceAccountPrivateKey);
		} catch (UnsupportedEncodingException e) {
			throw new GoogleAuthorizationException("Failed to process service account ID", e);
		}
	}

	/**
	 * Authorize service account using PEM encoded private key and service account ID as {@link String}
	 *
	 * <p> For details about "service account" scenario please refer to the <a href="https://developers.google.com/accounts/docs/OAuth2?hl=ru&csw=1#serviceaccount">link</a>.
	 *
	 * @param serviceAccountId            service account ID
	 * @param pemServiceAccountPrivateKey private key byte array in PEM format
	 * @return authorized {@link com.google.api.client.auth.oauth2.Credential} wrapper object
	 * @throws GoogleAuthorizationException if service account cannot be authorized
	 */
	public static Credential authorizeServiceAccount(String serviceAccountId, byte[] pemServiceAccountPrivateKey) throws GoogleAuthorizationException {
		checkNotNull(serviceAccountId, "Service account ID must be provided");
		checkNotNull(pemServiceAccountPrivateKey, "Service account PEM secret key must be provided");

		try {
			final byte[] bytes = Base64.decodeBase64(pemServiceAccountPrivateKey);
			final PrivateKey serviceAccountPrivateKey = SecurityUtils.getRsaKeyFactory().generatePrivate(new PKCS8EncodedKeySpec(bytes));

			GoogleCredential credential = new GoogleCredential.Builder().setTransport(HttpTransportFactory.getDefaultInstance())
					.setJsonFactory(JacksonFactory.getDefaultInstance())
					.setServiceAccountId(serviceAccountId)
					.setServiceAccountScopes(READ_WRITE_GCE_SCOPE)
					.setServiceAccountPrivateKey(serviceAccountPrivateKey)
					.build();

			credential.refreshToken();

			return credential;
		} catch (HttpResponseException e) {
			throw GoogleAuthorizationException.from(e, "Google failed to validate provided credentials");
		} catch (Exception e) {
			throw new GoogleAuthorizationException("Google failed to validate provided credentials", e);
		}
	}


	/**
	 * Authorize service account using <a href="http://en.wikipedia.org/wiki/PKCS_12">PKCS #12</a> certificate file bundle
	 *
	 * <p> For details about "service account" scenario please refer to the <a href="https://developers.google.com/accounts/docs/OAuth2?hl=ru&csw=1#serviceaccount">link</a>.
	 *
	 * @param serviceAccountId service account ID
	 * @param p12File          p12 file with private and public X.509 key
	 * @return authorized {@link com.google.api.client.auth.oauth2.Credential} wrapper object
	 * @throws GoogleAuthorizationException if service account cannot be authorized
	 * @deprecated currently it is planned to pass private key in PEM as a byte array {@link #authorizeServiceAccount(String, byte[])}
	 */
	public static Credential authorizeServiceAccount(String serviceAccountId, File p12File) throws GoogleAuthorizationException {
		checkNotNull(serviceAccountId, "Service account ID must be provided");
		checkArgument(p12File != null && p12File.exists() && p12File.canRead(), "Cannot access p12 key");

		try {
			GoogleCredential credential = new GoogleCredential.Builder().setTransport(HttpTransportFactory.getDefaultInstance())
					.setJsonFactory(JacksonFactory.getDefaultInstance())
					.setServiceAccountId(serviceAccountId)
					.setServiceAccountScopes(READ_WRITE_GCE_SCOPE)
					.setServiceAccountPrivateKeyFromP12File(p12File)
					.build();

			credential.refreshToken();

			return credential;
		} catch (HttpResponseException e) {
			throw GoogleAuthorizationException.from(e, "Google failed to validate provided credentials");
		} catch (Exception e) {
			throw new GoogleAuthorizationException("Google failed to validate provided credentials", e);
		}
	}

}
