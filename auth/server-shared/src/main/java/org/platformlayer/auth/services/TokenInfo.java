package org.platformlayer.auth.services;

import java.util.Date;

public class TokenInfo {
	public final byte flags;
	public final String userId;
	public final Date expiration;
	public final byte[] tokenSecret;

	public TokenInfo(byte flags, String userId, Date expiration, byte[] tokenSecret) {
		this.flags = flags;
		this.userId = userId;
		this.expiration = expiration;
		this.tokenSecret = tokenSecret;
	}

	public boolean hasExpired() {
		return expiration.getTime() < System.currentTimeMillis();
	}

}
