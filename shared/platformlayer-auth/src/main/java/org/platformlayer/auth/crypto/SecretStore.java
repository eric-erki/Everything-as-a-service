package org.platformlayer.auth.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.crypto.SecretKey;

import org.platformlayer.auth.ProjectInfo;
import org.platformlayer.auth.UserEntity;
import org.platformlayer.crypto.CryptoUtils;
import org.platformlayer.crypto.RsaUtils;
import org.platformlayer.model.ProjectAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fathomdb.crypto.CryptoKey;
import com.fathomdb.crypto.FathomdbCrypto;
import com.fathomdb.crypto.OpenSshUtils;
import com.fathomdb.hash.Md5Hash;
import com.fathomdb.io.IoUtils;
import com.google.common.io.ByteStreams;

public class SecretStore {
	private static final Logger log = LoggerFactory.getLogger(SecretStore.class);

	@Deprecated
	public static final byte ASYMETRIC_SYSTEM_KEY = 1;

	public static final byte USER_KEY = 2;
	public static final byte USER_PASSWORD = 3;
	public static final byte TOKEN = 4;
	public static final byte ASYMETRIC_USER_KEY = 5;
	public static final byte PROJECT_KEY = 6;
	public static final byte ASYMETRIC_PROJECT_KEY = 7;

	public static final byte ASYMETRIC_KEY = 8;

	byte[] encoded;

	public SecretStore(byte[] encoded) {
		this.encoded = encoded;
	}

	public Writer buildWriter() {
		return new Writer(new ByteArrayOutputStream());
	}

	public void appendContents(Writer writer) throws IOException {
		writer.dos.flush();
		writer.os.flush();

		ByteArrayOutputStream os = (ByteArrayOutputStream) writer.os;
		byte[] added = os.toByteArray();

		this.encoded = CryptoUtils.concat(encoded, added);
	}

	public byte[] getEncoded() {
		return encoded;
	}

	public static class Reader implements Closeable {
		final DataInputStream in;

		public Reader(InputStream is) {
			this.in = new DataInputStream(is);
		}

		@Override
		public void close() throws IOException {
			in.close();
		}

		public SecretKey read(SecretStoreVisitor visitor) throws IOException {
			while (true) {
				byte type;

				try {
					type = in.readByte();
				} catch (EOFException e) {
					break;
				}

				switch (type) {
				case ASYMETRIC_SYSTEM_KEY: {
					int keyId = readEncoded(in);
					byte[] data = readArray(in);
					visitor.visitAsymetricSystemKey(keyId, data);
					break;
				}

				case ASYMETRIC_KEY: {
					byte[] publicKeySignature = readArray(in);
					byte[] data = readArray(in);
					visitor.visitGenericAsymetricKey(publicKeySignature, data);
					break;
				}

				case ASYMETRIC_USER_KEY: {
					int userId = readEncoded(in);
					byte[] data = readArray(in);
					visitor.visitAsymetricUserKey(userId, data);
					break;
				}

				case USER_KEY: {
					int userId = readEncoded(in);
					byte[] data = readArray(in);
					visitor.visitUserKey(userId, data);
					break;
				}

				case PROJECT_KEY: {
					int projectId = readEncoded(in);
					byte[] data = readArray(in);
					visitor.visitProjectKey(projectId, data);
					break;
				}

				case USER_PASSWORD: {
					// int userId = readEncoded(in);
					byte[] salt = readArray(in);
					byte[] data = readArray(in);

					visitor.visitPassword(salt, data);
					break;
				}

				case TOKEN: {
					int tokenId = readEncoded(in);
					byte[] data = readArray(in);
					// Ignore
					// visitor.visitToken(tokenId, data);
					break;
				}

				default: {
					log.warn("Unexpected format: " + ((int) type));
					throw new IllegalArgumentException("Unexpected format");
				}
				}
			}

			return null;
		}

	}

	public static class Writer {
		final DataOutputStream dos;
		final OutputStream os;

		public Writer(OutputStream os) {
			this.os = os;
			this.dos = new DataOutputStream(os);
		}

		public void close() throws IOException {
			dos.close();
		}

		public void writeLockedByProjectKey(byte[] plaintext, int projectId, CryptoKey projectSecret)
				throws IOException {
			dos.writeByte(PROJECT_KEY);
			byte[] encrypted = FathomdbCrypto.encrypt(projectSecret, plaintext);
			writeEncoded(dos, projectId);
			writeArray(dos, encrypted);
		}

		public void writeLockedByUserKey(byte[] plaintext, int userId, CryptoKey userSecret) throws IOException {
			dos.writeByte(USER_KEY);
			byte[] encrypted = FathomdbCrypto.encrypt(userSecret, plaintext);
			writeEncoded(dos, userId);
			writeArray(dos, encrypted);
		}

		public void writeUserPassword(byte[] plaintext, String password) throws IOException {
			dos.writeByte(USER_PASSWORD);

			byte[] salt = CryptoUtils.generateSecureRandom(16);
			CryptoKey derivedKey = FathomdbCrypto.deriveKey(salt, password);

			byte[] encrypted = FathomdbCrypto.encrypt(derivedKey, plaintext);
			// writeEncoded(dos, userId);
			writeArray(dos, salt);
			writeArray(dos, encrypted);
		}

		public void writeLockedByToken(byte[] plaintext, int tokenId, byte[] tokenSecret) throws IOException {
			dos.writeByte(TOKEN);
			writeEncoded(dos, tokenId);

			byte[] encrypted = xorByteArrays(plaintext, tokenSecret);
			writeArray(dos, encrypted);
		}

		public void writeAsymetricSystemKey(byte[] plaintext, int backend, PublicKey publicKey) throws IOException {
			dos.writeByte(ASYMETRIC_SYSTEM_KEY);
			byte[] encrypted = RsaUtils.encrypt(publicKey, plaintext);
			writeEncoded(dos, backend);
			writeArray(dos, encrypted);
		}

		public void writeAsymetricUserKey(byte[] plaintext, int userId, PublicKey publicKey) throws IOException {
			dos.writeByte(ASYMETRIC_USER_KEY);
			byte[] encrypted = RsaUtils.encrypt(publicKey, plaintext);
			writeEncoded(dos, userId);
			writeArray(dos, encrypted);
		}

		public void writeGenericAsymetricKey(byte[] plaintext, PublicKey publicKey) throws IOException {
			dos.writeByte(ASYMETRIC_KEY);
			byte[] encrypted = RsaUtils.encrypt(publicKey, plaintext);
			Md5Hash signature = OpenSshUtils.getSignature(publicKey);
			writeArray(dos, signature.toByteArray());
			writeArray(dos, encrypted);
		}

		public void writeAsymetricProjectKey(byte[] plaintext, int projectId, PublicKey publicKey) throws IOException {
			dos.writeByte(ASYMETRIC_PROJECT_KEY);
			byte[] encrypted = RsaUtils.encrypt(publicKey, plaintext);
			writeEncoded(dos, projectId);
			writeArray(dos, encrypted);
		}

	}

	public static byte[] xorByteArrays(byte[] a, byte[] b) {
		if (a.length != b.length) {
			throw new IllegalArgumentException();
		}
		int len = a.length;
		byte[] ret = new byte[len];
		for (int i = 0; i < len; i++) {
			ret[i] = a[i];
			ret[i] ^= b[i];
		}
		return ret;
	}

	private static byte[] readArray(DataInputStream dis) throws IOException {
		int length = readEncoded(dis);
		byte[] array = new byte[length];
		ByteStreams.readFully(dis, array, 0, length);
		return array;
	}

	private static void writeArray(DataOutputStream dos, byte[] data) throws IOException {
		writeEncoded(dos, data.length);
		dos.write(data);
	}

	private static int readEncoded(DataInputStream dis) throws IOException {
		int v = 0;
		int shift = 0;

		do {
			byte k = dis.readByte();

			v |= (k & 0x7f) << shift;
			if ((k & 0x80) == 0) {
				return v;
			}

			shift += 7;
		} while (true);
	}

	private static void writeEncoded(DataOutputStream dos, int v) throws IOException {
		do {
			byte k = (byte) (v & 0x7f);
			v >>= 7;

			if (v != 0) {
				k |= 0x80;
			}
			dos.write(k);
		} while (v != 0);
	}

	public static void read(byte[] data, SecretStoreVisitor visitor) throws IOException {
		SecretStore.Reader reader = null;
		try {
			reader = new SecretStore.Reader(new ByteArrayInputStream(data));
			reader.read(visitor);
		} finally {
			IoUtils.safeClose(reader);
		}

	}

	public CryptoKey getSecretFromUser(final UserEntity user) {
		SecretStoreDecoder visitor = new SecretStoreDecoder() {
			@Override
			public void visitUserKey(int userId, byte[] data) {
				if (userId == user.getId()) {
					setSecretKey(decryptSymetricKey(user.getUserSecret(), data));
				}
			}

			@Override
			public void visitAsymetricUserKey(int userId, byte[] data) {
				if (userId == user.getId()) {
					PrivateKey privateKey = user.getPrivateKey();
					setSecretKey(decryptAsymetricKey(privateKey, data));
				}
			}
		};
		try {
			read(encoded, visitor);
		} catch (IOException e) {
			throw new IllegalArgumentException("Secret data is corrupted", e);
		}
		return visitor.getSecretKey();
	}

	public CryptoKey getSecretFromPassword(int userId, final String password) {
		SecretStoreDecoder visitor = new SecretStoreDecoder() {
			@Override
			public void visitPassword(byte[] salt, byte[] data) {
				CryptoKey secretKey = FathomdbCrypto.deriveKey(salt, password);
				setSecretKey(decryptSymetricKey(secretKey, data));
			}
		};
		try {
			read(encoded, visitor);
		} catch (IOException e) {
			throw new IllegalArgumentException("Secret data is corrupted", e);
		}
		return visitor.getSecretKey();
	}

	public CryptoKey getSecretFromProject(final ProjectInfo project) {
		return getSecretFromProject(project.getId(), project.getProjectSecret());
	}

	public CryptoKey getSecretFromProject(final ProjectAuthorization project) {
		return getSecretFromProject(project.getId(), project.getProjectSecret());
	}

	public CryptoKey getSecretFromProject(final int projectId, final CryptoKey projectSecret) {
		SecretStoreDecoder visitor = new SecretStoreDecoder() {
			@Override
			public void visitProjectKey(int itemProjectId, byte[] itemSecret) {
				if (projectId == itemProjectId) {
					setSecretKey(decryptSymetricKey(projectSecret, itemSecret));
				}
			}
		};
		try {
			read(encoded, visitor);
		} catch (IOException e) {
			throw new IllegalArgumentException("Secret data is corrupted", e);
		}
		return visitor.getSecretKey();
	}

	// public CryptoKey getSecretFromToken(final int tokenId, final byte[] tokenSecret) {
	// SecretStoreDecoder visitor = new SecretStoreDecoder() {
	// @Override
	// public void visitToken(int itemTokenId, byte[] itemData) {
	// if (itemTokenId == tokenId) {
	// // We want this to be reversible; so we XOR it.
	// // Both keys are random, so this is secure
	// if (tokenSecret.length == itemData.length) {
	// byte[] key = SecretStore.xorByteArrays(itemData, tokenSecret);
	// setSecretKey(FathomdbCrypto.deserializeKey(key));
	// }
	// }
	// }
	// };
	//
	// try {
	// read(encoded, visitor);
	// } catch (IOException e) {
	// throw new IllegalArgumentException("Secret data is corrupted", e);
	// }
	// return visitor.getSecretKey();
	// }

	// static class TokenSecretFinder extends SecretStoreVisitor {
	// final int tokenId;
	// final CryptoKey userSecret;
	//
	// public TokenSecretFinder(int tokenId, CryptoKey userSecret) {
	// this.tokenId = tokenId;
	// this.userSecret = userSecret;
	// }
	//
	// public byte[] tokenSecret;
	//
	// @Override
	// public void visitToken(int tokenId, byte[] data) {
	// if (tokenId == this.tokenId) {
	// byte[] userSecretBytes = FathomdbCrypto.serialize(userSecret);
	// this.tokenSecret = SecretStore.xorByteArrays(userSecretBytes, data);
	// }
	// }
	// }

	// public byte[] getTokenSecretWithUserSecret(int tokenId, CryptoKey userSecret) {
	// TokenSecretFinder visitor = new TokenSecretFinder(tokenId, userSecret);
	//
	// try {
	// read(encoded, visitor);
	// } catch (IOException e) {
	// throw new IllegalArgumentException("Secret data is corrupted", e);
	// }
	// return visitor.tokenSecret;
	// }

	public byte[] findChallengeForCertificate(X509Certificate[] certificateChain) {
		if (certificateChain.length == 0) {
			throw new IllegalArgumentException();
		}

		X509Certificate certificate = certificateChain[0];
		Md5Hash signature = OpenSshUtils.getSignature(certificate.getPublicKey());
		final byte[] signatureBytes = signature.toByteArray();

		SecretStoreDecoder visitor = new SecretStoreDecoder() {
			@Override
			public void visitGenericAsymetricKey(byte[] publicKeySignature, byte[] data) {
				if (Arrays.equals(publicKeySignature, signatureBytes)) {
					result = data;
				}
			}
		};

		try {
			read(encoded, visitor);
		} catch (IOException e) {
			throw new IllegalArgumentException("Secret data is corrupted", e);
		}

		return (byte[]) visitor.result;
	}

}
