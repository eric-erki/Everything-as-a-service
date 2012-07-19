package org.platformlayer.ops.standardservice;

import java.io.File;
import java.util.Map;

import javax.inject.Inject;

import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.ops.Command;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.crypto.ManagedSecretKey;
import org.platformlayer.ops.helpers.ProviderHelper;
import org.platformlayer.ops.machines.PlatformLayerHelpers;
import org.platformlayer.ops.templates.TemplateDataSource;

public abstract class StandardTemplateData implements TemplateDataSource {

	@Inject
	protected ProviderHelper providers;

	@Inject
	protected PlatformLayerHelpers platformLayer;

	public abstract ItemBase getModel();

	public String getServiceKey() {
		return getKey() + "-" + getInstanceKey();
	}

	public String getUser() {
		return getKey();
	}

	public String getGroup() {
		return getKey();
	}

	public File getInstallDir() {
		return new File("/opt", getKey());
	}

	public File getInstanceDir() {
		return new File(new File("/var", getKey()), getInstanceKey());
	}

	public File getConfigDir() {
		return new File(getInstanceDir(), "config");
	}

	public abstract String getKey();

	public String getInstanceKey() {
		return "default";
	}

	protected abstract Command getCommand();

	public File getConfigurationFile() {
		return new File(getConfigDir(), "configuration.properties");
	}

	protected abstract Map<String, String> getConfigurationProperties() throws OpsException;

	protected abstract PlatformLayerKey getSslKeyPath();

	public ManagedSecretKey findSslKey() throws OpsException {
		PlatformLayerKey sslKey = getSslKeyPath();
		if (sslKey == null) {
			return null;
		}
		ItemBase sslKeyItem = (ItemBase) platformLayer.getItem(sslKey);
		return providers.toInterface(sslKeyItem, ManagedSecretKey.class);
	}

	public boolean shouldCreateSslKey() {
		return true;
	}

	public File getKeystoreFile() {
		return new File(getConfigDir(), "../keystore.jks");
	}

	public File getDistFile() {
		return new File(getInstallDir(), getKey() + ".tar.gz");
	}

	public boolean shouldExpand() {
		String distFilename = getDistFile().getName();
		return distFilename.endsWith(".tar.gz") || distFilename.endsWith(".zip");
	}

	// public String getDatabaseName() {
	// return "main";
	// }
}
