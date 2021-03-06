package org.openstack.service.nginx.ops;

import org.openstack.service.nginx.model.NginxBackend;
import org.openstack.service.nginx.model.NginxService;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.Tag;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.UniqueTag;
import org.platformlayer.ops.firewall.Protocol;
import org.platformlayer.ops.tree.OwnedItem;
import org.platformlayer.service.network.v1.NetworkConnection;

import com.google.common.base.Strings;

public class NginxBackendFirewall extends OwnedItem {

	int port = 80;
	Protocol protocol = Protocol.Tcp;

	@Override
	protected ItemBase buildItemTemplate() {
		NginxService nginxService = OpsContext.get().getInstance(NginxService.class);
		NginxBackend nginxBackend = OpsContext.get().getInstance(NginxBackend.class);

		NetworkConnection networkConnection = new NetworkConnection();
		networkConnection.setDestItem(nginxBackend.backend);
		networkConnection.setSourceItem(nginxService.getKey());
		networkConnection.setPort(port);
		networkConnection.setProtocol(protocol.toString());

		Tag parentTag = Tag.buildParentTag(nginxBackend.getKey());
		networkConnection.getTags().add(parentTag);
		Tag uniqueTag = UniqueTag.build(nginxService, nginxBackend);
		networkConnection.getTags().add(uniqueTag);

		String id = nginxBackend.getId();
		if (Strings.isNullOrEmpty(id)) {
			id = "nginx";
		}
		networkConnection.key = PlatformLayerKey.fromId(id);

		return networkConnection;
	}

}
