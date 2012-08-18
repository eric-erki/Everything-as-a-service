package org.platformlayer.crypto;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import org.apache.log4j.Logger;
import org.platformlayer.IoUtils;
import org.platformlayer.ops.OpsException;

import com.google.common.collect.Lists;

public class CertificateReader {
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(CertificateReader.class);

	public X509Certificate[] parse(byte[] data) throws OpsException {
		return parse(new ByteArrayInputStream(data));
	}

	public X509Certificate[] parse(InputStream is) throws OpsException {
		List<X509Certificate> certs = Lists.newArrayList();
		try {
			CertificateFactory x509CertificateFactory = getX509CertificateFactory();
			while (is.available() > 0) {
				X509Certificate cert = (X509Certificate) x509CertificateFactory.generateCertificate(is);
				certs.add(cert);
			}
		} catch (CertificateException ce) {
			throw new OpsException("Not an X509 certificate", ce);
		} catch (IOException e) {
			throw new OpsException("Error reading certificates", e);
		}

		return certs.toArray(new X509Certificate[certs.size()]);
	}

	public X509Certificate[] parse(File file) throws OpsException, FileNotFoundException {
		FileInputStream fis = new FileInputStream(file);
		try {
			return parse(fis);
		} finally {
			IoUtils.safeClose(fis);
		}
	}

	public X509Certificate[] parse(String data) throws OpsException {
		return parse(data.getBytes());
	}

	CertificateFactory certificateFactory;

	private CertificateFactory getX509CertificateFactory() throws OpsException {
		if (certificateFactory == null) {
			try {
				certificateFactory = CertificateFactory.getInstance("X509");
			} catch (CertificateException e) {
				throw new OpsException("Error loading X509 provider", e);
			}
		}

		return certificateFactory;
	}

}
