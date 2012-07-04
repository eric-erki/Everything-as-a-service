package org.platformlayer.xaas;

import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.net.ssl.X509TrustManager;

import org.apache.log4j.Logger;
import org.platformlayer.crypto.CryptoUtils;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class PublicKeyTrustManager implements X509TrustManager {
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(PublicKeyTrustManager.class);

	private final Set<String> trusted;

	public PublicKeyTrustManager(Iterable<String> keys) {
		this.trusted = Sets.newHashSet();
		Iterables.addAll(this.trusted, keys);
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (chain.length == 0) {
			throw new IllegalArgumentException("null or zero-length certificate chain");
		}

		for (X509Certificate cert : chain) {
			PublicKey certPublicKey = cert.getPublicKey();

			String sigString = CryptoUtils.getSignature(certPublicKey);

			if (!trusted.contains(sigString)) {
				throw new CertificateException("Certificate is not in trusted list (" + sigString + ")");
			}
		}
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		throw new UnsupportedOperationException();
	}
}
