package org.platformlayer.auth.services.registration;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.platformlayer.CustomerFacingException;
import org.platformlayer.RepositoryException;
import org.platformlayer.auth.OpsUser;
import org.platformlayer.auth.UserDatabase;
import org.platformlayer.auth.services.RegistrationService;
import org.platformlayer.metrics.Instrumented;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

@Singleton
@Instrumented
public class RegistrationServiceImpl implements RegistrationService {
	private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

	private static final int MIN_PASSWORD_LENGTH = 6;

	@Inject
	UserDatabase repository;

	@Override
	public OpsUser registerUser(String username, String password) throws CustomerFacingException {
		if (Strings.isNullOrEmpty(username)) {
			throw CustomerFacingException.buildRequiredField("username");
		}

		if (Strings.isNullOrEmpty(password)) {
			throw CustomerFacingException.buildRequiredField("password");
		}

		password = password.trim();
		username = username.trim();

		checkPassword(password);

		checkUsername(username);

		OpsUser user;
		try {
			user = repository.findUser(username);
			if (user != null) {
				// TODO: Should we hide this fact?
				throw CustomerFacingException
						.buildFieldError("username", "duplicate", "Username is already registered");
			}

			user = repository.createUser(username, password, null);

			// TODO: We reserve @@, to prevent collisions
			// TODO: Is this good enough? What if project already exists?
			String projectKey = "user@@" + username.toLowerCase();
			repository.createProject(projectKey, user);
		} catch (RepositoryException e) {
			log.warn("Repository error creating user", e);
			throw CustomerFacingException.wrap(e);
		}

		return user;
	}

	private void checkUsername(String email) throws CustomerFacingException {
		if (!email.contains("@")) {
			throw CustomerFacingException.buildFieldError("email", "invalid", "Email address is invalid");
		}
		// TODO: More verification
	}

	private void checkPassword(String password) throws CustomerFacingException {
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw CustomerFacingException.buildFieldError("password", "invalid", "Password must be at least "
					+ MIN_PASSWORD_LENGTH + " characters");
		}
		// TODO: Minimum complexity?
	}
}
