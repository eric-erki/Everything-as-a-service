package org.platformlayer.crypto;

import org.openstack.crypto.StronglyTypedHash;
import org.openstack.utils.Hex;

public class Sha256Hash extends StronglyTypedHash {
	private static final int SHA256_BYTE_LENGTH = 256 / 8;

	public Sha256Hash(String hash) {
		this(Hex.fromHex(hash));
	}

	public Sha256Hash(byte[] sha) {
		super(sha);

		if (sha.length != SHA256_BYTE_LENGTH) {
			throw new IllegalArgumentException();
		}
	}
}
