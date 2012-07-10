package org.platformlayer.service.platformlayer.ops.auth.user;

import org.openstack.crypto.Md5Hash;
import org.platformlayer.ops.standardservice.StandardServiceInstall;
import org.platformlayer.ops.standardservice.StandardTemplateData;

public class UserAuthInstall extends StandardServiceInstall {
	@Override
	protected Md5Hash getMd5Hash() {
		// keystone-webapp-user-1.0-SNAPSHOT-bin.tar.gz
		Md5Hash hash = new Md5Hash("335e141cef0ab40dc36d903fa4f6d3fd");
		return hash;
	}

	@Override
	protected StandardTemplateData getTemplate() {
		return injected(UserAuthInstanceModel.class);
	}
}
