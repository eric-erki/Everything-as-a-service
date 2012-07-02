package org.openstack.keystone.service;

import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.openstack.docs.identity.api.v2.ProjectValidation;
import org.openstack.docs.identity.api.v2.Role;
import org.openstack.docs.identity.api.v2.UserValidation;
import org.openstack.docs.identity.api.v2.ValidateAccess;
import org.openstack.docs.identity.api.v2.ValidateTokenResponse;
import org.platformlayer.PlatformLayerClientException;
import org.platformlayer.WellKnownPorts;
import org.platformlayer.http.SimpleHttpRequest;
import org.platformlayer.model.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class KeystoneTokenValidator extends RestfulClient implements AuthenticationTokenValidator {
	static final Logger log = LoggerFactory.getLogger(KeystoneTokenValidator.class);

	public static final String DEFAULT_AUTHENTICATION_URL = "https://127.0.0.1:"
			+ WellKnownPorts.PORT_PLATFORMLAYER_AUTH_ADMIN + "/";

	final KeyManager keyManager;

	final TrustManager trustManager;

	final HostnameVerifier hostnameVerifier;

	public KeystoneTokenValidator(String baseUrl, KeyManager keyManager, TrustManager trustManager,
			HostnameVerifier hostnameVerifier) {
		super(baseUrl);
		this.keyManager = keyManager;
		this.trustManager = trustManager;
		this.hostnameVerifier = hostnameVerifier;
	}

	@Override
	protected void addHeaders(SimpleHttpRequest httpRequest) {
		httpRequest.setTrustManager(trustManager);
		httpRequest.setKeyManager(keyManager);
		if (hostnameVerifier != null) {
			httpRequest.setHostnameVerifier(hostnameVerifier);
		}
		// httpRequest.setRequestHeader(Keystone.AUTH_HEADER, authenticationToken);

	}

	// public KeystoneAuthenticationToken authenticate(String tenantName, PasswordCredentials passwordCredentials)
	// throws KeystoneAuthenticationException {
	// Auth auth = new Auth();
	// auth.setPasswordCredentials(passwordCredentials);
	// auth.setTenantName(tenantName);
	//
	// AuthenticateRequest request = new AuthenticateRequest();
	// request.setAuth(auth);
	//
	// AuthenticateResponse response = doSimpleRequest("POST", "tokens", request, AuthenticateResponse.class);
	// return new KeystoneAuthenticationToken(response.getAccess());
	// }

	@Override
	public Authentication validate(String authToken) {
		// v2.0/tokens/{userToken}[?belongsTo={tenant}]

		authToken = authToken.trim();

		String url = "v2.0/tokens/" + authToken;

		try {
			ValidateTokenResponse response = doSimpleRequest("GET", url, null, ValidateTokenResponse.class);

			ValidateAccess access = response.getAccess();

			ProjectValidation project = access.getProject();
			String projectId = project.getId();
			if (projectId == null) {
				return null;
			}

			List<String> roles = Lists.newArrayList();
			UserValidation userInfo = access.getUser();
			for (Role role : userInfo.getRoles()) {
				if (!role.getTenantId().equals(projectId)) {
					throw new IllegalStateException("Tenant mismatch: " + role.getTenantId() + " vs " + projectId);
				}
				roles.add(role.getName());
			}

			byte[] userSecret = userInfo.getSecret();
			String userKey = userInfo.getName();

			KeystoneAuthentication auth = new KeystoneAuthentication(userKey, project, userSecret, roles);
			return auth;
		} catch (PlatformLayerClientException e) {
			if (e.getHttpResponseCode() != null && e.getHttpResponseCode() == 404) {
				// Not found => invalid token
				return null;
			}
			throw new IllegalArgumentException("Error while validating token", e);
		}
	}
}
