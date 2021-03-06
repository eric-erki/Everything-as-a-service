package org.platformlayer.ops.databases;

import java.security.cert.X509Certificate;
import java.util.Map;

import org.platformlayer.InetAddressChooser;
import org.platformlayer.core.model.Secret;
import org.platformlayer.ops.OpsException;

public interface DatabaseServer {
	String getJdbcUrl(String database, InetAddressChooser chooser) throws OpsException;

	Secret getRootPassword();

	String getRootUsername();

	DatabaseTarget buildDatabaseTarget(String username, Secret password, String databaseName) throws OpsException;

	X509Certificate[] getCertificateChain() throws OpsException;

	Map<String, String> buildTargetConfiguration(String username, Secret password, String databaseName,
			InetAddressChooser inetAddressChooser) throws OpsException;
}
