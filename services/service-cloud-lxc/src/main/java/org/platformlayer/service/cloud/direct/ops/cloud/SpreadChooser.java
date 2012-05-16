package org.platformlayer.service.cloud.direct.ops.cloud;

import java.util.List;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.platformlayer.choice.Chooser;
import org.platformlayer.choice.ScoreChooser;
import org.platformlayer.core.model.HostPolicy;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.Tag;
import org.platformlayer.ops.Injection;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.machines.PlatformLayerHelpers;
import org.platformlayer.service.cloud.direct.model.DirectInstance;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class SpreadChooser implements Chooser<DirectCloudHost> {
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(SpreadChooser.class);

	@Inject
	PlatformLayerHelpers platformLayer;

	String groupId;

	class HostRecord {
		List<DirectInstance> matchingPolicy = Lists.newArrayList();
		DirectCloudHost candidate;
	}

	@Override
	public DirectCloudHost choose(List<DirectCloudHost> candidates) throws OpsException {
		List<HostRecord> records = Lists.newArrayList();
		for (DirectCloudHost candidate : candidates) {
			HostRecord record = new HostRecord();
			record.candidate = candidate;
			records.add(record);

			for (Tag tag : candidate.getModel().getTags().findTags(Tag.ASSIGNED)) {
				PlatformLayerKey instanceKey = PlatformLayerKey.parse(tag.getValue());

				// TODO: Avoid 1+N
				DirectInstance instance = platformLayer.getItem(instanceKey);
				if (instance == null) {
					// TODO: Warn?
					throw new IllegalStateException();
				}

				switch (instance.getState()) {
				case DELETE_REQUESTED:
				case DELETED:
					continue;
				}

				HostPolicy hostPolicy = instance.hostPolicy;

				if (Objects.equal(groupId, hostPolicy.groupId)) {
					record.matchingPolicy.add(instance);
				}
			}
		}

		Function<HostRecord, Integer> score = new Function<HostRecord, Integer>() {
			@Override
			public Integer apply(HostRecord input) {
				return input.matchingPolicy.size();
			}
		};

		HostRecord bestRecord = ScoreChooser.chooseMin(score).choose(records);

		return bestRecord.candidate;
	}

	public static SpreadChooser build(String groupId) {
		if (Strings.isNullOrEmpty(groupId)) {
			throw new IllegalArgumentException();
		}

		SpreadChooser chooser = Injection.getInstance(SpreadChooser.class);
		chooser.groupId = groupId;
		return chooser;
	}
}