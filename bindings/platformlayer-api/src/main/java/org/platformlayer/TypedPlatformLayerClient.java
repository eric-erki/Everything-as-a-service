package org.platformlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.platformlayer.common.UntypedItem;
import org.platformlayer.common.UntypedItemCollection;
import org.platformlayer.core.model.Action;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.ServiceInfo;
import org.platformlayer.core.model.Tag;
import org.platformlayer.core.model.TagChanges;
import org.platformlayer.core.model.Tags;
import org.platformlayer.ids.ManagedItemId;
import org.platformlayer.ids.ProjectId;
import org.platformlayer.jobs.model.JobData;
import org.platformlayer.jobs.model.JobDataList;
import org.platformlayer.jobs.model.JobExecutionList;
import org.platformlayer.jobs.model.JobLog;
import org.platformlayer.metrics.model.MetricDataStream;
import org.platformlayer.metrics.model.MetricInfoCollection;
import org.platformlayer.metrics.model.MetricQuery;
import org.platformlayer.ops.OpsException;
import org.platformlayer.xml.JaxbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class TypedPlatformLayerClient implements PlatformLayerClient {
	private static final Logger log = LoggerFactory.getLogger(TypedPlatformLayerClient.class);

	final PlatformLayerClient platformLayerClient;
	final TypedItemMapper mapper;

	@Inject
	public TypedPlatformLayerClient(PlatformLayerClient platformLayerClient, TypedItemMapper mapper) {
		this.platformLayerClient = platformLayerClient;
		this.mapper = mapper;
	}

	public <T> T promoteToTyped(UntypedItem untypedItem) throws OpsException {
		return mapper.promoteToTyped(untypedItem);
	}

	public <T> T promoteToTyped(UntypedItem untypedItem, Class<T> itemClass) throws OpsException {
		return mapper.promoteToTyped(untypedItem, itemClass);
	}

	// public ItemBase getItem(String path) throws OpsException {
	// ItemBase item = findItem(path);
	// if (item == null)
	// throw new OpsException("Item not found: " + item);
	// return item;
	// }
	//
	// public ItemBase findItem(String path) throws OpsException {
	// UntypedItem cloudItemUntyped = platformLayerClient.getUntypedItem(path);
	// if (cloudItemUntyped == null) {
	// return null;
	// }
	//
	// return promoteToTyped(cloudItemUntyped);
	// }

	public <T> T getItem(PlatformLayerKey path, Class<T> itemClass) throws OpsException {
		T item = findItem(path, itemClass);
		if (item == null) {
			throw new OpsException("Item not found: " + path);
		}
		return item;
	}

	public <T> T getItem(PlatformLayerKey path) throws OpsException {
		T item = findItem(path);
		if (item == null) {
			throw new OpsException("Item not found: " + path);
		}
		return item;
	}

	@Override
	public <T> T findItem(PlatformLayerKey path, Class<T> itemClass) throws OpsException {
		return platformLayerClient.findItem(path, itemClass);
	}

	public <T> T findItem(String id, Class<T> itemClass) throws OpsException {
		PlatformLayerKey key = toKey(itemClass, id);

		return findItem(key, itemClass);
	}

	public <T> PlatformLayerKey toKey(Class<T> itemClass, String id) throws PlatformLayerClientException {
		JaxbHelper jaxbHelper = PlatformLayerClientBase.toJaxbHelper(itemClass, new Class[0]);
		ManagedItemId itemId = new ManagedItemId(id);

		PlatformLayerKey key = PlatformLayerClientBase.toKey(jaxbHelper, itemId, itemClass,
				platformLayerClient.listServices(true));
		return key;
	}

	@Override
	public <T> T findItem(PlatformLayerKey path) throws OpsException {
		return platformLayerClient.findItem(path);
	}

	/**
	 * If using directly, consider using OwnedItem instead
	 */
	@Override
	@Deprecated
	public <T extends ItemBase> T putItemByTag(T item, Tag uniqueTag) throws OpsException {
		return platformLayerClient.putItemByTag(item, uniqueTag);
	}

	/**
	 * Consider using putItemByTag instead (or OwnedItem) for idempotency
	 */
	@Override
	@Deprecated
	public <T extends ItemBase> T putItem(T item) throws OpsException {
		return platformLayerClient.putItem(item);
	}

	// public <T> Iterable<T> listItems(Class<T> itemClass) throws PlatformLayerClientException {
	// return platformLayerClient.listItems(itemClass);
	// }

	@Override
	public JobData deleteItem(PlatformLayerKey key) throws PlatformLayerClientException {
		return platformLayerClient.deleteItem(key);
	}

	public Tags addTags(PlatformLayerKey key, List<Tag> tags) throws PlatformLayerClientException {
		TagChanges changeTags = new TagChanges();

		changeTags.addTags.addAll(tags);

		return changeTags(key, changeTags);
	}

	public Tags addTag(PlatformLayerKey key, Tag tag) throws PlatformLayerClientException {
		return addTags(key, Collections.singletonList(tag));
	}

	public Tags addUniqueTag(PlatformLayerKey key, Tag tag) throws PlatformLayerClientException {
		// Sometimes we require idempotency; we can do this using unique tags.
		// TODO: Implement this
		log.warn("addUniqueTag not properly implemented");
		return addTag(key, tag);
	}

	// public <T> Iterable<T> listItems(Class<T> itemClass, Filter filter) throws PlatformLayerClientException {
	// return platformLayerClient.listItems(itemClass, filter);
	// }

	public <T> List<T> listItems(Class<T> clazz, boolean includeDeleted) throws OpsException {
		Filter filter = null;
		if (!includeDeleted) {
			filter = StateFilter.excludeDeleted(filter);
		}
		return listItems(clazz, filter);
	}

	@Override
	public <T> List<T> listItems(Class<T> clazz) throws OpsException {
		return listItems(clazz, false);
	}

	public <T> List<T> listItems(Class<T> clazz, PlatformLayerKey parent) throws OpsException {
		boolean includeDeleted = false;

		Filter filter = TagFilter.byParent(parent);
		if (!includeDeleted) {
			filter = StateFilter.excludeDeleted(filter);
		}

		return listItems(clazz, filter);
	}

	public <T> List<T> listItems(Class<T> clazz, Filter filter) throws OpsException {
		// JaxbHelper jaxbHelper = PlatformLayerClientBase.toJaxbHelper(clazz, ManagedItemCollection.class);
		// PlatformLayerKey path = PlatformLayerClientBase.toKey(jaxbHelper, null,
		// platformLayerClient.listServices(true));
		//
		// List<T> items = Lists.newArrayList();
		//
		// for (UntypedItem untypedItem : untypedItems.getItems()) {
		// T item = promoteToTyped(untypedItem, clazz);
		// items.add(item);
		// }

		List<T> items = this.platformLayerClient.listItems(clazz);

		if (filter != null) {
			// TODO: Do filtering server-side
			List<T> filtered = Lists.newArrayList();
			for (T item : items) {
				if (filter.matches(item)) {
					filtered.add(item);
				}
			}
			return filtered;
		} else {
			return items;
		}
	}

	public Tags changeTags(PlatformLayerKey key, TagChanges tagChanges) throws PlatformLayerClientException {
		return changeTags(key, tagChanges, null);
	}

	@Override
	public Tags changeTags(PlatformLayerKey key, TagChanges tagChanges, Long ifVersion)
			throws PlatformLayerClientException {
		return platformLayerClient.changeTags(key, tagChanges, ifVersion);
	}

	@Override
	public ProjectId getProject() {
		return platformLayerClient.getProject();
	}

	@Override
	public JobData doAction(PlatformLayerKey key, Action action) throws PlatformLayerClientException {
		return platformLayerClient.doAction(key, action);
	}

	@Override
	public JobData doAction(PlatformLayerKey key, String action, Format dataFormat) throws PlatformLayerClientException {
		return platformLayerClient.doAction(key, action, dataFormat);
	}

	/**
	 * Consider using putItemByTag instead (or OwnedItem) for idempotency
	 */
	@Deprecated
	@Override
	public UntypedItem putItem(PlatformLayerKey key, String data, Format dataFormat)
			throws PlatformLayerClientException {
		return platformLayerClient.putItem(key, data, dataFormat);
	}

	/**
	 * If using directly, consider using OwnedItem instead
	 */
	@Deprecated
	@Override
	public UntypedItem putItemByTag(PlatformLayerKey key, Tag uniqueTag, String data, Format dataFormat)
			throws PlatformLayerClientException {
		return platformLayerClient.putItemByTag(key, uniqueTag, data, dataFormat);
	}

	@Override
	public UntypedItem getItemUntyped(PlatformLayerKey key, Format format) throws PlatformLayerClientException {
		return platformLayerClient.getItemUntyped(key, format);
	}

	public UntypedItem getItemUntyped(PlatformLayerKey key) throws PlatformLayerClientException {
		return getItemUntyped(key, Format.XML);
	}

	@Override
	public Tags getItemTags(PlatformLayerKey key) throws PlatformLayerClientException {
		return platformLayerClient.getItemTags(key);
	}

	@Override
	public UntypedItemCollection listItemsUntyped(PlatformLayerKey path) throws PlatformLayerClientException {
		return platformLayerClient.listItemsUntyped(path);
	}

	@Override
	public UntypedItemCollection listRoots() throws PlatformLayerClientException {
		return platformLayerClient.listRoots();
	}

	@Override
	public UntypedItemCollection listChildren(PlatformLayerKey parent, boolean includeDeleted)
			throws PlatformLayerClientException {
		return platformLayerClient.listChildren(parent, includeDeleted);
	}

	@Override
	public List<ItemBase> listChildrenTyped(PlatformLayerKey parent, boolean includeDeleted) throws OpsException {
		return platformLayerClient.listChildrenTyped(parent, includeDeleted);
	}

	@Override
	public JobDataList listJobs() throws PlatformLayerClientException {
		return platformLayerClient.listJobs();
	}

	@Override
	public JobDataList listJobs(PlatformLayerKey target) throws PlatformLayerClientException {
		return platformLayerClient.listJobs(target);
	}

	@Override
	public MetricDataStream getMetric(MetricQuery query) throws PlatformLayerClientException {
		return platformLayerClient.getMetric(query);
	}

	@Override
	public MetricInfoCollection listMetrics(PlatformLayerKey key) throws PlatformLayerClientException {
		return platformLayerClient.listMetrics(key);
	}

	@Override
	public Collection<ServiceInfo> listServices(boolean allowCache) throws PlatformLayerClientException {
		return platformLayerClient.listServices(allowCache);
	}

	@Override
	public String activateService(String serviceType, String data, Format format) throws PlatformLayerClientException {
		return platformLayerClient.activateService(serviceType, data, format);
	}

	@Override
	public String getActivation(String serviceType, Format format) throws PlatformLayerClientException {
		return platformLayerClient.getActivation(serviceType, format);
	}

	@Override
	public String getSshPublicKey(String serviceType) throws PlatformLayerClientException {
		return platformLayerClient.getSshPublicKey(serviceType);
	}

	@Override
	public String getSchema(String serviceType, Format format) throws PlatformLayerClientException {
		return platformLayerClient.getSchema(serviceType, format);
	}

	@Override
	public void ensureLoggedIn() throws PlatformLayerAuthenticationException {
		platformLayerClient.ensureLoggedIn();
	}

	@Override
	public PlatformLayerEndpointInfo getEndpointInfo(PlatformLayerKey item) {
		return platformLayerClient.getEndpointInfo(item);
	}

	@Override
	public JobLog getJobExecutionLog(String jobId, String executionId) throws PlatformLayerClientException {
		return platformLayerClient.getJobExecutionLog(jobId, executionId);
	}

	@Override
	public JobExecutionList listJobExecutions(String jobId) throws PlatformLayerClientException {
		return platformLayerClient.listJobExecutions(jobId);
	}

	@Override
	public JobExecutionList listJobExecutions() throws PlatformLayerClientException {
		return platformLayerClient.listJobExecutions();
	}

}
