package org.openstack.service.nginx.ops;

import javax.inject.Inject;

import org.openstack.service.nginx.model.NginxFrontend;
import org.openstack.service.nginx.model.NginxService;
import org.platformlayer.ops.CustomRecursor;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.Injection;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.helpers.ServiceContext;
import org.platformlayer.ops.tree.ForEach;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NginxSites extends OpsTreeBase implements CustomRecursor {

	private static final Logger log = LoggerFactory.getLogger(NginxSites.class);

	@Handler
	public void handler() {
	}

	@Inject
	ServiceContext service;

	@Override
	protected void addChildren() throws OpsException {
		addChild(injected(NginxSiteConfig.class));
		addChild(injected(NginxSiteEnabledSymlink.class));
		addChild(injected(NginxFrontendDns.class));
	}

	@Override
	public void doRecurseOperation() throws OpsException {
		ForEach recursor = Injection.getInstance(ForEach.class);

		recursor.doRecursion(this, service.getSshKey(), NginxService.class, NginxFrontend.class);
	}

}
