package org.platformlayer.service.gerrit.ops;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.openstack.utils.Utf8;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.filesystem.SyntheticFile;
import org.platformlayer.ops.machines.PlatformLayerHelpers;

import com.google.common.collect.LinkedHashMultimap;

public class GerritConfigurationFile extends SyntheticFile {
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(GerritConfigurationFile.class);

	@Inject
	PlatformLayerHelpers platformLayer;

	private GerritInstanceModel getTemplate() {
		GerritInstanceModel template = injected(GerritInstanceModel.class);
		return template;
	}

	@Override
	protected File getFilePath() {
		return getTemplate().getConfigurationFile();
	}

	@Override
	protected byte[] getContentsBytes() throws OpsException {
		Map<String, String> model = getTemplate().getConfigurationProperties();

		String contents = serialize(model);

		return Utf8.getBytes(contents);
	}

	private String serialize(Map<String, String> model) throws OpsException {
		LinkedHashMultimap<String, String> map = LinkedHashMultimap.create();

		for (Entry<String, String> entry : model.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			int dotIndex = key.indexOf('.');
			if (dotIndex == -1) {
				throw new OpsException("Invalid key format: " + key);
			}

			String heading = key.substring(0, dotIndex);
			String subKey = key.substring(dotIndex + 1);

			map.put(heading, subKey + " = " + value);
		}

		StringBuilder sb = new StringBuilder();

		for (String heading : map.keySet()) {
			sb.append("[" + heading + "]\n");
			for (String value : map.get(heading)) {
				sb.append("\t" + value + "\n");
			}
		}

		return sb.toString();
	}

}