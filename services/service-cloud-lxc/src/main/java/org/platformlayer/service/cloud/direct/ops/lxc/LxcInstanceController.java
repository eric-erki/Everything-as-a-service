package org.platformlayer.service.cloud.direct.ops.lxc;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.platformlayer.PlatformLayerClient;
import org.platformlayer.core.model.Tag;
import org.platformlayer.core.model.TagChanges;
import org.platformlayer.crypto.OpenSshUtils;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsProvider;
import org.platformlayer.ops.OpsSystem;
import org.platformlayer.ops.filesystem.ManagedDirectory;
import org.platformlayer.ops.helpers.ImageFactory;
import org.platformlayer.ops.helpers.InstanceHelpers;
import org.platformlayer.ops.helpers.ServiceContext;
import org.platformlayer.ops.helpers.SshKeys;
import org.platformlayer.ops.images.ImageFormat;
import org.platformlayer.ops.networks.AddressModel;
import org.platformlayer.ops.pool.NetworkAddressPoolAssignment;
import org.platformlayer.ops.tagger.Tagger;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.platformlayer.service.cloud.direct.model.DirectInstance;
import org.platformlayer.service.cloud.direct.ops.CloudInstanceMapper;
import org.platformlayer.service.cloud.direct.ops.DirectCloudUtils;
import org.platformlayer.service.cloud.direct.ops.DownloadImage;
import org.platformlayer.service.cloud.direct.ops.cloud.CloudMap;

public class LxcInstanceController extends OpsTreeBase {
	static final Logger log = Logger.getLogger(LxcInstanceController.class);

	public File instanceDir;
	public String id;
	public int minimumMemoryMB;

	@Inject
	PlatformLayerClient platformLayer;

	@Inject
	ImageFactory imageFactory;

	@Inject
	ServiceContext service;

	@Inject
	InstanceHelpers instances;

	@Inject
	CloudMap cloudMap;

	@Inject
	ImageFactory images;

	@Inject
	PlatformLayerClient platformLayerClient;

	@Inject
	SshKeys sshKeys;

	@Handler
	public void handler() throws OpsException, IOException {
	}

	@Override
	protected void addChildren() throws OpsException {
		final DirectInstance model = OpsContext.get().getInstance(DirectInstance.class);

		CloudInstanceMapper instance;
		{
			instance = injected(CloudInstanceMapper.class);
			instance.instance = OpsContext.get().getInstance(DirectInstance.class);
			addChild(instance);
		}

		instance.addChild(ManagedDirectory.build(getInstanceDir(), "700"));

		// TODO: If we're not going to assign an IPV4 redirect, we might not need this
		final NetworkAddressPoolAssignment address4;
		{
			address4 = instance.addChild(NetworkAddressPoolAssignment.class);
			address4.holder = getInstanceDir();
			address4.poolProvider = DirectCloudUtils.getPrivateAddressPool4();
		}

		final NetworkAddressPoolAssignment address6;
		{
			address6 = instance.addChild(NetworkAddressPoolAssignment.class);
			address6.holder = getInstanceDir();
			address6.poolProvider = DirectCloudUtils.getAddressPool6();
		}

		// {
		// NetworkTunDevice tun = injected(NetworkTunDevice.class);
		// tun.interfaceName = getEthernetDeviceName();
		// tun.bridgeName = Providers.getProperty(assignNetworkAddress, "bridge");
		// instance.addChild(tun);
		// }

		{
			DownloadImage download = injected(DownloadImage.class);
			download.imageFile = new File(getInstanceDir(), "rootfs");
			download.recipeKey = model.recipeId;
			download.imageFormats = Collections.singletonList(ImageFormat.Tar);
			instance.addChild(download);
		}

		{
			LxcBootstrap bootstrap = injected(LxcBootstrap.class);
			bootstrap.address4 = address4;
			bootstrap.address6 = address6;
			bootstrap.lxcId = id;
			bootstrap.instanceDir = instanceDir;

			try {
				bootstrap.sshPublicKey = OpenSshUtils.readSshPublicKey(model.sshPublicKey);
			} catch (IOException e) {
				throw new OpsException("Error deserializing SSH key", e);
			}

			bootstrap.hostname = model.hostname;

			instance.addChild(bootstrap);
		}

		{
			ManagedLxcInstance kvmInstance = injected(ManagedLxcInstance.class);

			kvmInstance.id = id;
			kvmInstance.base = getInstanceDir();

			instance.addChild(kvmInstance);
		}

		{
			OpsProvider<TagChanges> tagChanges = new OpsProvider<TagChanges>() {
				@Override
				public TagChanges get() {
					TagChanges tagChanges = new TagChanges();

					tagChanges.addTags.add(new Tag(Tag.INSTANCE_KEY, OpsSystem.toKey(model).getUrl()));

					AddressModel ipv4 = address4.get();
					AddressModel ipv6 = address6.get();

					tagChanges.addTags.add(ipv4.toTag());
					tagChanges.addTags.add(ipv6.toTag());

					return tagChanges;
				}
			};

			instance.addChild(Tagger.build(model, tagChanges));
		}
	}

	private File getInstanceDir() {
		return instanceDir;
	}
}
