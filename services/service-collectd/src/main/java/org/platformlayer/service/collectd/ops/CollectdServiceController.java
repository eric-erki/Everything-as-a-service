package org.platformlayer.service.collectd.ops;

import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.instances.DiskImageRecipeBuilder;
import org.platformlayer.ops.instances.InstanceBuilder;
import org.platformlayer.ops.metrics.collectd.CollectdSink;
import org.platformlayer.ops.packages.PackageDependency;
import org.platformlayer.ops.service.ManagedService;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.platformlayer.service.collectd.model.CollectdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class CollectdServiceController extends OpsTreeBase {
	private static final Logger log = LoggerFactory.getLogger(CollectdServiceController.class);

	@Override
	protected void addChildren() throws OpsException {
		CollectdService model = OpsContext.get().getInstance(CollectdService.class);
		if (Strings.isNullOrEmpty(model.dnsName)) {
			throw new IllegalArgumentException("dnsName must be specified");
		}

		// We'd like to auto-gen the disk image, but we have to fix the problems involving pre-installing collectd (see
		// below)
		InstanceBuilder instance = InstanceBuilder.build(model.dnsName,
				DiskImageRecipeBuilder.loadDiskImageResource(getClass(), "DiskImageRecipe.xml"), model.getTags());
		instance.minimumMemoryMb = 512; // Make sure we have a bit more RAM, so that we can queue up a fair amount of
										// RRD data
		addChild(instance);

		// We have some problems using collectd with debootstrap; I think it's when we're using FQDN and we can't
		// resolve the hostname
		// See http://thegrebs.com/irc/debian/2011/04/01

		instance.addChild(PackageDependency.build("librrd4"));
		instance.addChild(PackageDependency.build("rrdcached"));

		// collectd is a bit fussy, so we have problems bundling it into the disk image
		// TODO: This sucks - collectd is pretty big...
		instance.addChild(PackageDependency.build("collectd"));

		// collectd has collectdmon to keep it alive; don't use monit (for now)

		instance.addChild(CollectdSink.build());

		instance.addChild(ManagedService.build("collectd"));
	}
}
