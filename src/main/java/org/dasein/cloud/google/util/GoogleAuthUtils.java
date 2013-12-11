package org.dasein.cloud.google.util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.SecurityUtils;
import com.google.common.collect.ImmutableSet;

import java.io.File;
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
	 * <p> Authorize service account using PEM encoded private key
	 *
	 * <p> For details about "service account" scenario please refer to the <a href="https://developers.google.com/accounts/docs/OAuth2?hl=ru&csw=1#serviceaccount">link</a>.
	 *
	 * @param serviceAccountId            service account ID
	 * @param pemServiceAccountPrivateKey private key byte array in PEM format
	 * @return authorized {@link com.google.api.client.auth.oauth2.Credential} wrapper object
	 */
	public static Credential authorizeServiceAccount(String serviceAccountId, byte[] pemServiceAccountPrivateKey) {
		checkNotNull(serviceAccountId);
		checkNotNull(pemServiceAccountPrivateKey);

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
		} catch (Exception e) {
			throw new RuntimeException("Failed to create google credentials", e);
		}
	}


	/**
	 * <p> Authorize service account using <a href="http://en.wikipedia.org/wiki/PKCS_12">PKCS #12</a> certificate file bundle
	 *
	 * <p> For details about "service account" scenario please refer to the <a href="https://developers.google.com/accounts/docs/OAuth2?hl=ru&csw=1#serviceaccount">link</a>.
	 *
	 * @param serviceAccountId service account ID
	 * @param p12File   p12 file with private and public X.509 key
	 * @return authorized {@link com.google.api.client.auth.oauth2.Credential} wrapper object
	 * @deprecated currently it is planned to pass private key in PEM as a byte array {@link #authorizeServiceAccount(String, byte[])}
	 */
	private static Credential authorizeServiceAccount(String serviceAccountId, File p12File) throws Exception {
		checkNotNull(serviceAccountId);
		checkArgument(p12File != null && p12File.exists() && p12File.canRead(), "cannot access p12 key");

		try {
			GoogleCredential credential = new GoogleCredential.Builder().setTransport(HttpTransportFactory.getDefaultInstance())
					.setJsonFactory(JacksonFactory.getDefaultInstance())
					.setServiceAccountId(serviceAccountId)
					.setServiceAccountScopes(READ_WRITE_GCE_SCOPE)
					.setServiceAccountPrivateKeyFromP12File(p12File)
					.build();

			credential.refreshToken();

			return credential;
		} catch (Exception e) {
			throw new RuntimeException("Failed to create google credentials", e);
		}
	}

}
