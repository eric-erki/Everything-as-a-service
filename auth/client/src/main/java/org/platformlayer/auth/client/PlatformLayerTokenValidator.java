package org.platformlayer.auth.client;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.openstack.crypto.CertificateAndKey;
import org.platformlayer.WellKnownPorts;
import org.platformlayer.auth.AuthenticationTokenValidator;
import org.platformlayer.auth.PlatformlayerProjectAuthorization;
import org.platformlayer.auth.PlatformlayerUserAuthentication;
import org.platformlayer.auth.v1.ProjectValidation;
import org.platformlayer.auth.v1.UserValidation;
import org.platformlayer.auth.v1.ValidateAccess;
import org.platformlayer.auth.v1.ValidateTokenResponse;
import org.platformlayer.config.Configuration;
import org.platformlayer.crypto.AcceptAllHostnameVerifier;
import org.platformlayer.crypto.EncryptionStore;
import org.platformlayer.crypto.PublicKeyTrustManager;
import org.platformlayer.crypto.SimpleClientCertificateKeyManager;
import org.platformlayer.model.AuthenticationToken;
import org.platformlayer.model.ProjectAuthorization;
import org.platformlayer.ops.OpsException;
import org.platformlayer.rest.RestClientException;
import org.platformlayer.rest.RestfulClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

public class PlatformLayerTokenValidator extends RestfulClient implements AuthenticationTokenValidator {
	static final Logger log = LoggerFactory.getLogger(PlatformLayerTokenValidator.class);

	public static final String DEFAULT_AUTHENTICATION_URL = "https://127.0.0.1:"
			+ WellKnownPorts.PORT_PLATFORMLAYER_AUTH_ADMIN + "/";

	private PlatformLayerTokenValidator(String baseUrl, KeyManager keyManager, TrustManager trustManager,
			HostnameVerifier hostnameVerifier) {
		super(baseUrl, keyManager, trustManager, hostnameVerifier);
	}

	public static PlatformLayerTokenValidator build(Configuration configuration, EncryptionStore encryptionStore)
			throws OpsException {
		String keystoneServiceUrl = configuration.lookup("auth.system.url", "https://127.0.0.1:"
				+ WellKnownPorts.PORT_PLATFORMLAYER_AUTH_ADMIN + "/");

		String cert = configuration.get("auth.system.ssl.cert");
		// String secret = configuration.lookup("multitenant.cert.password", KeyStoreUtils.DEFAULT_KEYSTORE_SECRET);

		CertificateAndKey certificateAndKey = encryptionStore.getCertificateAndKey(cert);

		HostnameVerifier hostnameVerifier = null;

		KeyManager keyManager = new SimpleClientCertificateKeyManager(certificateAndKey);

		TrustManager trustManager = null;

		String trustKeys = configuration.lookup("auth.system.ssl.keys", null);

		if (trustKeys != null) {
			trustManager = new PublicKeyTrustManager(Splitter.on(',').trimResults().split(trustKeys));

			hostnameVerifier = new AcceptAllHostnameVerifier();
		}

		PlatformLayerTokenValidator keystoneTokenValidator = new PlatformLayerTokenValidator(keystoneServiceUrl,
				keyManager, trustManager, hostnameVerifier);
		return keystoneTokenValidator;
	}

	@Override
	public ProjectAuthorization validate(AuthenticationToken authToken, String projectId) {
		// v2.0/tokens/{userToken}[?belongsTo={tenant}]

		String tokenId = ((PlatformlayerAuthenticationToken) authToken).getAuthTokenValue();
		tokenId = tokenId.trim();

		String url = "v2.0/tokens/" + tokenId;

		url += "?project=" + urlEncode(projectId);

		try {
			ValidateTokenResponse response = doSimpleRequest("GET", url, null, ValidateTokenResponse.class);

			ValidateAccess access = response.getAccess();

			if (access == null) {
				return null;
			}

			// ProjectValidation project = access.getProject();
			// if (project == null || !Objects.equal(projectId, project.getId())) {
			// return null;
			// }

			UserValidation userInfo = access.getUser();

			if (userInfo == null) {
				return null;
			}

			ProjectValidation projectInfo = access.getProject();
			if (projectInfo == null) {
				return null;
			}

			// List<String> roles = Lists.newArrayList();
			// UserValidation userInfo = access.getUser();
			// for (Role role : userInfo.getRoles()) {
			// if (!role.getTenantId().equals(projectId)) {
			// throw new IllegalStateException("Tenant mismatch: " + role.getTenantId() + " vs " + projectId);
			// }
			// roles.add(role.getName());
			// }

			byte[] userSecret = userInfo.getSecret();
			String userKey = userInfo.getName();

			PlatformlayerUserAuthentication user = new PlatformlayerUserAuthentication(authToken, userKey, userSecret);
			PlatformlayerProjectAuthorization project = new PlatformlayerProjectAuthorization(user, projectInfo);
			return project;
		} catch (RestClientException e) {
			if (e.getHttpResponseCode() != null && e.getHttpResponseCode() == 404) {
				// Not found => invalid token
				return null;
			}
			throw new IllegalArgumentException("Error while validating token", e);
		}
	}
}
