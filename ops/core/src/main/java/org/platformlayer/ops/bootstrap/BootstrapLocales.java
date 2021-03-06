package org.platformlayer.ops.bootstrap;

import java.io.File;

import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.filesystem.SimpleFile;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BootstrapLocales extends OpsTreeBase {
	private static final Logger log = LoggerFactory.getLogger(BootstrapLocales.class);

	@Handler
	public void handler() {

	}

	@Override
	protected void addChildren() throws OpsException {
		addChild(SimpleFile.build(getClass(), RegenerateLocales.LOCALE_GEN_FILE));
		addChild(RegenerateLocales.class);
		addChild(SimpleFile.build(getClass(), new File("/etc/default/locale")));

	}
}
