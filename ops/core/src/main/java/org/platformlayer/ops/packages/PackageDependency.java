package org.platformlayer.ops.packages;

import java.util.List;

import javax.inject.Inject;

import org.platformlayer.images.model.ConfigurePackage;
import org.platformlayer.images.model.DiskImageRecipe;
import org.platformlayer.images.model.Repository;
import org.platformlayer.images.model.RepositoryKey;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.HasDescription;
import org.platformlayer.ops.Injection;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsTarget;
import org.platformlayer.ops.instances.HasDiskImageRecipe;
import org.platformlayer.ops.tree.OpsItemBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class PackageDependency extends OpsItemBase implements HasDiskImageRecipe, HasDescription {
	private static final Logger log = LoggerFactory.getLogger(PackageDependency.class);

	public String packageName;

	@Inject
	AptPackageManager apt;

	public Repository repository;

	public RepositoryKey repositoryKey;

	public List<ConfigurePackage> configuration;

	@Handler
	public void doOperation() throws OpsException {
		if (OpsContext.isDelete()) {
			// Should we delete the packages? Probably not, because others may also need them
			log.debug("Not removing package on delete (in case someone else also uses it)");
		}

		if (OpsContext.isConfigure() || OpsContext.isValidate()) {
			OpsTarget target = OpsContext.get().getInstance(OpsTarget.class);
			List<String> installedPackages = apt.getInstalledPackageInfo(target);

			if (!installedPackages.contains(packageName)) {
				log.info("Package not installed: " + packageName);

				if (OpsContext.isValidate()) {
					throw new OpsException("Package not installed: " + packageName);
				}

				if (repositoryKey != null) {
					apt.addRepositoryKeyUrl(target, repositoryKey.getUrl());
				}

				if (repository != null) {
					apt.addRepository(target, repository.getKey(), repository.getSource());
				}

				if (configuration != null) {
					apt.preconfigurePackages(target, configuration);
				}

				// TODO: Only update once per operation?
				// I think we do want to update aggressively though, because we want to be sure we're up to date
				// as that could well be the reason we're running the operation!

				// We definitely want to update if we added a repository etc above
				apt.update(target, false);

				apt.install(target, packageName);
			} else {
				log.debug("Package is installed: " + packageName);
			}
		}
	}

	public static PackageDependency build(String packageName) {
		PackageDependency packageDependency = Injection.getInstance(PackageDependency.class);
		packageDependency.packageName = packageName;
		return packageDependency;
	}

	@Override
	public void addTo(DiskImageRecipe recipe) {
		if (repositoryKey != null) {
			recipe.getRepositoryKey().add(repositoryKey);
		}
		if (repository != null) {
			recipe.getRepository().add(repository);
		}

		if (configuration != null) {
			recipe.getConfigurePackage().addAll(configuration);
		}

		recipe.getAddPackage().add(packageName);
	}

	public void addConfiguration(String packageName, String key, String type, String value) {
		if (configuration == null) {
			configuration = Lists.newArrayList();
		}

		ConfigurePackage conf = new ConfigurePackage();
		conf.setKey(key);
		conf.setValue(value);
		conf.setType(type);
		conf.setPackageName(packageName);

		configuration.add(conf);
	}

	public static List<PackageDependency> build(String... packageNames) {
		List<PackageDependency> packages = Lists.newArrayList();
		for (String packageName : packageNames) {
			packages.add(build(packageName));
		}
		return packages;
	}

	public static List<PackageDependency> build(Iterable<String> packageNames) {
		List<PackageDependency> packages = Lists.newArrayList();
		for (String packageName : packageNames) {
			packages.add(build(packageName));
		}
		return packages;
	}

	@Override
	public String getDescription() {
		return "APT package: " + packageName;
	}
}
