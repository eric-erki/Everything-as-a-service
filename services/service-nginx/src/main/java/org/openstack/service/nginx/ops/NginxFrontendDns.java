package org.openstack.service.nginx.ops;

import javax.inject.Inject;

import org.openstack.service.nginx.model.NginxFrontend;
import org.openstack.service.nginx.model.NginxService;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.Tag;
import org.platformlayer.dns.model.DnsRecord;
import org.platformlayer.ops.Machine;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.UniqueTag;
import org.platformlayer.ops.helpers.InstanceHelpers;
import org.platformlayer.ops.networks.NetworkPoint;
import org.platformlayer.ops.tree.OwnedItem;

public class NginxFrontendDns extends OwnedItem<DnsRecord> {

	@Inject
	InstanceHelpers instanceHelpers;

	@Override
	protected DnsRecord buildItemTemplate() throws OpsException {
		// TODO: Idempotency etc
		// Machine machine = OpsContext.get().getInstance(Machine.class);
		NginxService nginxService = OpsContext.get().getInstance(NginxService.class);
		NginxFrontend nginxFrontend = OpsContext.get().getInstance(NginxFrontend.class);

		Machine machine = instanceHelpers.getMachine(nginxService);

		String address = machine.getNetworkPoint().getBestAddress(NetworkPoint.forPublicInternet());

		DnsRecord record = new DnsRecord();
		record.setDnsName(nginxFrontend.hostname);
		record.getAddress().add(address);

		Tag parentTag = Tag.buildParentTag(nginxFrontend.getKey());
		record.getTags().add(parentTag);
		Tag uniqueTag = UniqueTag.build(nginxService, nginxFrontend);
		record.getTags().add(uniqueTag);

		record.key = PlatformLayerKey.fromId(nginxFrontend.hostname);

		return record;
	}

}
