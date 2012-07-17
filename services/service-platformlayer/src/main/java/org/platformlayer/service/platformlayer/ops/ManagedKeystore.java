package org.platformlayer.service.platformlayer.ops;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.inject.Inject;
import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;
import org.openstack.crypto.KeyStoreUtils;
import org.platformlayer.ResourceUtils;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.Tag;
import org.platformlayer.core.model.TagChanges;
import org.platformlayer.crypto.CertificateReader;
import org.platformlayer.crypto.CryptoUtils;
import org.platformlayer.ops.FileUpload;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsTarget;
import org.platformlayer.ops.crypto.ManagedSecretKey;
import org.platformlayer.ops.firewall.Sanitizer;
import org.platformlayer.ops.firewall.Sanitizer.Decision;
import org.platformlayer.ops.machines.PlatformLayerHelpers;
import org.platformlayer.ops.tree.OpsTreeBase;

import sun.security.x509.X500Name;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

public class ManagedKeystore extends OpsTreeBase {
	public static final String DEFAULT_WEBSERVER_ALIAS = "https";

	public File path;

	public String keystoreSecret = "notasecret";

	public String alias = "selfsigned";

	public ItemBase tagWithPublicKeys;

	private static final Logger log = Logger.getLogger(ManagedKeystore.class);

	@Inject
	PlatformLayerHelpers platformlayer;

	public ManagedSecretKey key;

	@Handler
	public void handler(OpsTarget target) throws OpsException {
		KeyStore keystore = null;
		boolean dirty = false;

		List<String> keyAliases;

		{
			byte[] data = target.readBinaryFile(path);
			try {
				if (data != null) {
					keystore = KeyStoreUtils.load(data, keystoreSecret);
				} else {
					keystore = KeyStoreUtils.createEmpty(keystoreSecret);
					dirty = true;
				}

				keyAliases = KeyStoreUtils.getKeyAliases(keystore);
			} catch (GeneralSecurityException e) {
				throw new OpsException("Error reading keystore", e);
			} catch (IOException e) {
				throw new OpsException("Error reading keystore", e);
			}
		}

		if (keyAliases.contains(alias)) {
			try {
				Certificate[] certificateChain = keystore.getCertificateChain(alias);
				if (certificateChain == null || certificateChain.length == 0) {
					keyAliases.remove(alias);
				} else {
					Certificate certificate = certificateChain[0];

					boolean remove = false;
					if (key != null) {
						if (!Objects.equal(key.getCertificate(), certificate)) {
							log.warn("Key found, but mismatch on certificate; will remove");
							remove = true;
						}
					}

					if (remove) {
						// TODO: Rename instead??
						keystore.deleteEntry(alias);
						dirty = true;
						keyAliases.remove(alias);
					}
				}
			} catch (KeyStoreException e) {
				throw new OpsException("Error reading from keystore", e);
			}

		}
		if (!keyAliases.contains(alias)) {
			if (key == null) {
				insertSelfSignedKey(keystore);
			} else {
				insertKey(keystore, key);
			}

			dirty = true;
			keyAliases.add(alias);
		}

		if (tagWithPublicKeys != null) {
			List<String> publicKeySigs = Lists.newArrayList();

			try {
				// for (String alias : keyAliases) {
				Certificate[] cert = keystore.getCertificateChain(alias);
				if (cert.length == 0) {
					log.warn("Ignoring zero length certificate chain for: " + alias);
					// continue;
				} else {
					PublicKey certPublicKey = cert[0].getPublicKey();

					String sigString = CryptoUtils.getSignatureString(certPublicKey);
					publicKeySigs.add(sigString);
				}
				// }
			} catch (GeneralSecurityException e) {
				throw new OpsException("Error reading public keys", e);
			}

			List<String> existingSigs = Tag.PUBLIC_KEY_SIG.find(tagWithPublicKeys);

			List<String> missing = Lists.newArrayList();
			for (String publicKeySig : publicKeySigs) {
				if (!existingSigs.contains(publicKeySig)) {
					missing.add(publicKeySig);
				}
			}

			if (!missing.isEmpty()) {
				TagChanges tagChanges = new TagChanges();
				for (String add : missing) {
					tagChanges.addTags.add(Tag.PUBLIC_KEY_SIG.build(add));
				}
				platformlayer.changeTags(tagWithPublicKeys.getKey(), tagChanges);
			}
		}

		if (dirty) {
			byte[] data;
			try {
				data = KeyStoreUtils.serialize(keystore, keystoreSecret);
			} catch (GeneralSecurityException e) {
				throw new OpsException("Error serializing keystore", e);
			} catch (IOException e) {
				throw new OpsException("Error serializing keystore", e);
			}
			FileUpload.upload(target, path, data);
		}
	}

	private void insertSelfSignedKey(KeyStore keystore) throws OpsException {
		String keyPassword = KeyStoreUtils.DEFAULT_KEYSTORE_SECRET;

		int validityDays = 365 * 10;
		String subjectDN = "CN=platformlayer";

		X500Name x500Name;
		try {
			x500Name = new X500Name(subjectDN);
		} catch (IOException e) {
			throw new OpsException("Error building X500 name", e);
		}

		try {
			KeyStoreUtils.createSelfSigned(keystore, alias, keyPassword, x500Name, validityDays);
		} catch (GeneralSecurityException e) {
			throw new OpsException("Error creating self-signed certificate", e);
		}
	}

	private void insertKey(KeyStore keystore, ManagedSecretKey key) throws OpsException {
		String keyPassword = KeyStoreUtils.DEFAULT_KEYSTORE_SECRET;

		X509Certificate certificate = key.getCertificate();

		List<X509Certificate> certificateChain = Lists.newArrayList();
		certificateChain.add(certificate);

		ensureCertificateChain(keystore, certificateChain);

		Key privateKey = key.getPrivateKey();

		X509Certificate[] certificateChainArray = certificateChain
				.toArray(new X509Certificate[certificateChain.size()]);
		try {
			keystore.setKeyEntry(alias, privateKey, keyPassword.toCharArray(), certificateChainArray);
		} catch (KeyStoreException e) {
			throw new OpsException("Error while installing private key", e);
		}
	}

	void ensureCertificateChain(KeyStore keyStore, List<X509Certificate> certificateChain) throws OpsException {
		while (true) {
			X509Certificate tail = certificateChain.get(certificateChain.size() - 1);

			if (isSelfSigned(tail)) {
				break;
			}

			X500Principal issuer = tail.getIssuerX500Principal();
			X509Certificate issuerCert = findIssuerCertificate(issuer);
			if (issuerCert == null) {
				throw new OpsException("Cannot find certificate: " + issuer);
			}

			String alias = sanitizeX500Principal(issuer);

			try {
				if (keyStore.containsAlias(alias)) {
					throw new OpsException("Keystore already has alias");
				}

				keyStore.setCertificateEntry(alias, issuerCert);
			} catch (KeyStoreException e) {
				throw new OpsException("Error setting key into keystore", e);
			}

			certificateChain.add(issuerCert);

			if (certificateChain.size() > 64) {
				throw new OpsException("Likely certificate chain loop detected");
			}
		}
	}

	private X509Certificate findIssuerCertificate(X500Principal issuer) throws OpsException {
		String resource = sanitizeX500Principal(issuer);

		resource = "certificates/" + resource.toLowerCase() + ".crt";

		byte[] issuerCertData = null;
		try {
			issuerCertData = ResourceUtils.findBinary(getClass(), resource);
		} catch (IOException e) {
			log.warn("Error while reading resource: " + resource, e);
		}

		if (issuerCertData == null) {
			log.warn("Resource not found: " + resource);
			throw new OpsException("Cannot find certificate for: " + issuer);
		}

		CertificateReader reader = new CertificateReader();
		Certificate issuerCert = reader.parse(issuerCertData);

		if (issuerCert == null) {
			throw new OpsException("Error reading certificate: " + issuer);
		}

		return (X509Certificate) issuerCert;
	}

	private String sanitizeX500Principal(X500Principal issuer) {
		Sanitizer sanitizer = new Sanitizer(Decision.Block, '_');
		sanitizer.allowAlphanumeric().setCombineBlocked(true);

		return sanitizer.clean(issuer.getName());
	}

	private static boolean isSelfSigned(X509Certificate cert) {
		return cert.getSubjectDN().equals(cert.getIssuerDN());
	}

	@Override
	protected void addChildren() throws OpsException {

	}
}