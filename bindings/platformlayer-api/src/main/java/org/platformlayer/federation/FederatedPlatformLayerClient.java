package org.platformlayer.federation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.platformlayer.CheckedFunction;
import org.platformlayer.Format;
import org.platformlayer.PlatformLayerAuthenticationException;
import org.platformlayer.PlatformLayerClient;
import org.platformlayer.PlatformLayerClientBase;
import org.platformlayer.PlatformLayerClientException;
import org.platformlayer.PlatformLayerClientNotFoundException;
import org.platformlayer.PlatformLayerEndpointInfo;
import org.platformlayer.TypedItemMapper;
import org.platformlayer.TypedPlatformLayerClient;
import org.platformlayer.UntypedItemXml;
import org.platformlayer.common.UntypedItem;
import org.platformlayer.common.UntypedItemCollection;
import org.platformlayer.common.UntypedItemCollectionBase;
import org.platformlayer.core.model.Action;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.ManagedItemCollection;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.ServiceInfo;
import org.platformlayer.core.model.Tag;
import org.platformlayer.core.model.TagChanges;
import org.platformlayer.core.model.Tags;
import org.platformlayer.federation.model.FederationConfiguration;
import org.platformlayer.federation.model.FederationRule;
import org.platformlayer.federation.model.PlatformLayerConnectionConfiguration;
import org.platformlayer.forkjoin.FakeForkJoinStrategy;
import org.platformlayer.forkjoin.ForkJoinStrategy;
import org.platformlayer.forkjoin.ListConcatentation;
import org.platformlayer.ids.FederationKey;
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
import org.platformlayer.service.federation.v1.FederatedService;
import org.platformlayer.service.federation.v1.FederatedServiceMap;
import org.platformlayer.xml.JaxbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class FederatedPlatformLayerClient extends PlatformLayerClientBase {
	private static final Logger log = LoggerFactory.getLogger(FederatedPlatformLayerClient.class);

	// TODO: We could maybe do this with a Dynamic Proxy (i.e. MethodInvocation magic)??

	final ForkJoinStrategy forkJoinPool;

	final Map<FederationMapping, ChildClient> childClients = Maps.newHashMap();

	final FederationMap federationMap;

	final ProjectId defaultProject;

	public FederatedPlatformLayerClient(TypedItemMapper mapper, ProjectId defaultProject, FederationMap federationMap,
			ForkJoinStrategy forkJoinPool) {
		super(mapper);

		this.defaultProject = defaultProject;
		this.federationMap = federationMap;
		this.forkJoinPool = forkJoinPool;

		buildClients();
	}

	// public static FederatedPlatformLayerClient buildUsingSavedConfiguration(String key) throws IOException {
	// File credentialsFile = new File(System.getProperty("user.home") + File.separator + ".credentials" +
	// File.separator + key);
	// if (!credentialsFile.exists())
	// throw new FileNotFoundException("Configuration file not found: " + credentialsFile);
	// Properties properties = new Properties();
	// try {
	// properties.load(new FileInputStream(credentialsFile));
	// } catch (IOException e) {
	// throw new IOException("Error reading configuration file: " + credentialsFile, e);
	// }
	// return buildUsingProperties(properties);
	// }

	// public static FederatedPlatformLayerClient buildUsingConfig(InputStream is, TypedItemMapper mapper)
	// throws OpsException {
	// FederationConfiguration federationMapConfig = SmartDeserialization.deserialize(FederationConfiguration.class,
	// is);
	// FederationMap federationMap = new FederationMap(mapper, federationMapConfig);
	//
	// // int parallelism = Runtime.getRuntime().availableProcessors();
	// // // Because we're doing lots of HTTP requests, rather than being CPU bound, we massively increase the
	// // parallelism
	// // parallelism *= 256;
	//
	// ForkJoinStrategy forkJoinPool = new FakeForkJoinStrategy();
	//
	// return new FederatedPlatformLayerClient(federationMap, forkJoinPool);
	// }

	void buildClients() {
		// TODO: Fork/Join?

		for (FederationMapping key : federationMap.getAllTargetKeys()) {
			TypedPlatformLayerClient client = federationMap.buildClient(key);

			ChildClient child = new ChildClient(key, client);
			childClients.put(key, child);
		}

		if (childClients.isEmpty()) {
			throw new IllegalStateException();
		}
	}

	static class ChildClient {
		public final FederationMapping key;
		public final TypedPlatformLayerClient client;

		public ChildClient(FederationMapping key, TypedPlatformLayerClient client) {
			this.key = key;
			this.client = client;

			if (key == null) {
				throw new IllegalStateException();
			}
		}

		// public UntypedItem setHost(UntypedItemXml item) {
		// // if (!key.equals(FederationKey.LOCAL_FEDERATION_KEY)) {
		// PlatformLayerKey plk = item.getKey();
		// item.setPlatformLayerKey(changeHost(plk));
		// // }
		// return item;
		// }

		public JobData setHost(JobData item) {
			// if (!key.equals(FederationKey.LOCAL_FEDERATION_KEY)) {
			PlatformLayerKey plk = item.key;
			item.key = changeHost(plk);
			// }
			return item;
		}

		private PlatformLayerKey changeHost(PlatformLayerKey plk) {
			return new PlatformLayerKey(key.host, key.project, plk.getServiceType(), plk.getItemType(), plk.getItemId());
		}

		public <T> T setHost(T item) {
			// if (!key.equals(FederationKey.LOCAL_FEDERATION_KEY)) {
			if (item instanceof ItemBase) {
				ItemBase itemBase = (ItemBase) item;

				// if (!key.equals(FederationKey.LOCAL_FEDERATION_KEY)) {
				PlatformLayerKey plk = itemBase.getKey();
				if (plk == null) {
					throw new IllegalStateException();
				}
				itemBase.setKey(changeHost(plk));
				// }
			} else if (item instanceof UntypedItemXml) {
				UntypedItemXml untypedItemXml = (UntypedItemXml) item;
				PlatformLayerKey plk = untypedItemXml.getKey();
				untypedItemXml.setPlatformLayerKey(changeHost(plk));
			} else {
				throw new UnsupportedOperationException();
			}
			// }

			return item;
		}

		@Override
		public String toString() {
			return "ChildClient [key=" + key + "]";
		}
	}

	static class MappedPlatformLayerKey {
		public ChildClient child;
		public PlatformLayerKey key;
	}

	// @Override
	// public <T> Iterable<T> listItems(Class<T> clazz) throws PlatformLayerClientException {
	// return listItems(clazz, null);
	// }
	//
	// @Override
	// public <T> Iterable<T> listItems(final Class<T> clazz, Filter filter) throws PlatformLayerClientException {
	// PlatformLayerKey key = toKey(clazz);
	// return doListConcatenation(getChildClients(key), AddHostTyped.wrap(new ListItemsTyped<T>(clazz, filter)));
	// }
	static abstract class HostFunction<V> implements CheckedFunction<ChildClient, V, PlatformLayerClientException> {
		@Override
		public abstract V apply(final ChildClient child) throws PlatformLayerClientException;
	}

	static class ListItemsUntyped extends HostFunction<UntypedItemCollection> {
		final PlatformLayerKey path;

		public ListItemsUntyped(PlatformLayerKey path) {
			this.path = path;
		}

		@Override
		public UntypedItemCollection apply(final ChildClient child) throws PlatformLayerClientException {
			return child.client.listItemsUntyped(path);
		}
	}

	static class ListItemsTyped<T> extends HostFunction<List<T>> {
		private final Class<T> clazz;

		public ListItemsTyped(Class<T> clazz) {
			this.clazz = clazz;
		}

		@Override
		public List<T> apply(final ChildClient child) throws PlatformLayerClientException {
			try {
				return child.client.listItems(clazz);
			} catch (OpsException e) {
				throw new PlatformLayerClientException("Error listing items", e);
			}
		}
	}

	static class ListChildren extends HostFunction<UntypedItemCollection> {
		final PlatformLayerKey parent;
		final boolean includeDeleted;

		public ListChildren(PlatformLayerKey parent, boolean includeDeleted) {
			super();
			this.parent = parent;
			this.includeDeleted = includeDeleted;
		}

		@Override
		public UntypedItemCollection apply(final ChildClient child) throws PlatformLayerClientException {
			try {
				return child.client.listChildren(parent, includeDeleted);
			} catch (PlatformLayerClientNotFoundException e) {
				log.warn("Ignoring not found from federated client on: " + e.getUrl());
				return UntypedItemCollectionBase.empty();
			}
		}
	}

	// static class ListItemsTyped<T> extends HostFunction<Iterable<T>> {
	// final Class<T> clazz;
	// final Filter filter;
	//
	// public ListItemsTyped(Class<T> clazz, Filter filter) {
	// this.clazz = clazz;
	// this.filter = filter;
	// }
	//
	// public Iterable<T> apply(final ChildClient child) throws PlatformLayerClientException {
	// return child.client.listItems(clazz, filter);
	// }
	// }

	static class ListServices extends HostFunction<Iterable<ServiceInfo>> {
		final boolean allowCache;

		public ListServices(boolean allowCache) {
			this.allowCache = allowCache;
		}

		@Override
		public Iterable<ServiceInfo> apply(final ChildClient child) throws PlatformLayerClientException {
			return child.client.listServices(allowCache);
		}
	}

	static class ListRoots extends HostFunction<UntypedItemCollection> {
		@Override
		public UntypedItemCollection apply(final ChildClient child) throws PlatformLayerClientException {
			return child.client.listRoots();
		}
	}

	static class ListJobs extends HostFunction<JobDataList> {
		final PlatformLayerKey target;

		public ListJobs(PlatformLayerKey target) {
			super();
			this.target = target;
		}

		@Override
		public JobDataList apply(final ChildClient child) throws PlatformLayerClientException {
			if (target != null) {
				return child.client.listJobs();
			} else {
				return child.client.listJobs(target);
			}
		}
	}

	static class AddHostUntyped extends HostFunction<UntypedItemCollection> {
		final HostFunction<UntypedItemCollection> inner;

		public AddHostUntyped(HostFunction<UntypedItemCollection> inner) {
			this.inner = inner;
		}

		public static AddHostUntyped wrap(HostFunction<UntypedItemCollection> inner) {
			return new AddHostUntyped(inner);
		}

		@Override
		public UntypedItemCollection apply(final ChildClient child) throws PlatformLayerClientException {
			UntypedItemCollection innerItems = inner.apply(child);
			Iterable<UntypedItem> items = Iterables.transform(innerItems.getItems(),
					new Function<UntypedItem, UntypedItem>() {
						@Override
						public UntypedItem apply(UntypedItem item) {
							child.setHost(item);
							return item;
						}
					});
			return new UntypedItemCollectionBase(items);
		}
	}

	static class AddHostTyped<T> extends HostFunction<Iterable<T>> {
		final HostFunction<? extends Iterable<T>> inner;

		public AddHostTyped(HostFunction<? extends Iterable<T>> inner) {
			this.inner = inner;
		}

		public static <T> AddHostTyped<T> wrap(HostFunction<? extends Iterable<T>> inner) {
			return new AddHostTyped<T>(inner);
		}

		@Override
		public Iterable<T> apply(final ChildClient child) throws PlatformLayerClientException {
			return Iterables.transform(inner.apply(child), new Function<T, T>() {
				@Override
				public T apply(T item) {
					child.setHost(item);
					return item;
				}
			});
		}
	}

	static class AddHostToJob extends HostFunction<JobDataList> {
		final HostFunction<JobDataList> inner;

		public AddHostToJob(HostFunction<JobDataList> inner) {
			this.inner = inner;
		}

		public static AddHostToJob wrap(HostFunction<JobDataList> inner) {
			return new AddHostToJob(inner);
		}

		@Override
		public JobDataList apply(final ChildClient child) throws PlatformLayerClientException {
			JobDataList ret = JobDataList.create();
			JobDataList innerJobs = inner.apply(child);
			Iterable<JobData> outerJobs = Iterables.transform(innerJobs.getJobs(), new Function<JobData, JobData>() {
				@Override
				public JobData apply(JobData item) {
					child.setHost(item);
					return item;
				}
			});

			ret.jobs = Lists.newArrayList(outerJobs);

			return ret;
		}
	}

	@Override
	public UntypedItemCollection listItemsUntyped(final PlatformLayerKey path) throws PlatformLayerClientException {
		return doListConcatenationUntyped(getChildClients(path), AddHostUntyped.wrap(new ListItemsUntyped(path)));
	}

	@Override
	public <T> List<T> listItems(final Class<T> clazz) throws PlatformLayerClientException {
		JaxbHelper jaxbHelper = PlatformLayerClientBase.toJaxbHelper(clazz, ManagedItemCollection.class);
		PlatformLayerKey path = PlatformLayerClientBase.toKey(jaxbHelper, null, listServices(true));

		return doListConcatenationTyped(getChildClients(path), AddHostTyped.wrap(new ListItemsTyped<T>(clazz)));
	}

	private <V> Iterable<V> doListConcatenation(Iterable<ChildClient> childClients, HostFunction<Iterable<V>> function)
			throws PlatformLayerClientException {
		try {
			return ListConcatentation.joinLists(forkJoinPool, childClients, function);
		} catch (ExecutionException e) {
			throw new PlatformLayerClientException("Error while building item list", e);
		}
	}

	private UntypedItemCollection doListConcatenationUntyped(Iterable<ChildClient> childClients,
			HostFunction<UntypedItemCollection> function) throws PlatformLayerClientException {
		try {
			return ListConcatentation.joinListsUntypedItems(forkJoinPool, childClients, function);
		} catch (ExecutionException e) {
			throw new PlatformLayerClientException("Error while building item list", e);
		}
	}

	private <T> List<T> doListConcatenationTyped(Iterable<ChildClient> childClients,
			HostFunction<? extends Iterable<T>> function) throws PlatformLayerClientException {
		try {
			return ListConcatentation.joinListsTypedItems(forkJoinPool, childClients, function);
		} catch (ExecutionException e) {
			throw new PlatformLayerClientException("Error while building item list", e);
		}
	}

	private JobDataList doListConcatenationJobs(Iterable<ChildClient> childClients, HostFunction<JobDataList> function)
			throws PlatformLayerClientException {
		try {
			return ListConcatentation.joinListsJobs(forkJoinPool, childClients, function);
		} catch (ExecutionException e) {
			throw new PlatformLayerClientException("Error while building item list", e);
		}
	}

	// @Override
	// public <T> T getItem(final Class<T> clazz, final PlatformLayerKey key) throws PlatformLayerClientException {
	// MappedPlatformLayerKey mapped = mapToChild(key);
	// T item = mapped.child.client.getItem(clazz, mapped.key);
	// return mapped.child.setHost(item);
	// }

	// @Override
	// public <T> T createItem(T item) throws PlatformLayerClientException {
	// PlatformLayerKey key = toKey(item);
	//
	// MappedPlatformLayerKey mapped = mapToChildForCreate(key);
	//
	// T created = mapped.child.client.createItem(item);
	// return mapped.child.setHost(created);
	// }
	//
	// @Override
	// public String createItem(ServiceType serviceType, ItemType itemType, String data, Format format) throws
	// PlatformLayerClientException {
	// throw new UnsupportedOperationException();
	//
	// // PlatformLayerKey key = new PlatformLayerKey(serviceType, itemType, null);
	// // MappedPlatformLayerKey mapped = mapToChild(key);
	// // String s = child.client.createItem(serviceType, itemType, data, format);
	// // return child.setHost(s);
	// }

	@Override
	public UntypedItem putItem(PlatformLayerKey key, String data, Format format) throws PlatformLayerClientException {
		MappedPlatformLayerKey mapped = mapToChildForPut(key);

		UntypedItemXml untypedItem = UntypedItemXml.build(data);
		untypedItem.setPlatformLayerKey(mapped.key);

		UntypedItem item = mapped.child.client.putItem(mapped.key, untypedItem.serialize(), format);

		return mapped.child.setHost(item);
	}

	@Override
	public JobData deleteItem(PlatformLayerKey key) throws PlatformLayerClientException {
		MappedPlatformLayerKey mapped = mapToChild(key);
		JobData jobData = mapped.child.client.deleteItem(key);
		return mapped.child.setHost(jobData);
	}

	@Override
	public UntypedItem getItemUntyped(PlatformLayerKey key, Format format) throws PlatformLayerClientException {
		MappedPlatformLayerKey mapped = mapToChild(key);
		UntypedItem item = mapped.child.client.getItemUntyped(key);
		return mapped.child.setHost(item);
	}

	@Override
	public UntypedItemCollection listRoots() throws PlatformLayerClientException {
		return doListConcatenationUntyped(getChildClients(), AddHostUntyped.wrap(new ListRoots()));
	}

	@Override
	public JobDataList listJobs() throws PlatformLayerClientException {
		return doListConcatenationJobs(getChildClients(), AddHostToJob.wrap(new ListJobs(null)));
	}

	@Override
	public JobDataList listJobs(PlatformLayerKey target) throws PlatformLayerClientException {
		return doListConcatenationJobs(getChildClients(), AddHostToJob.wrap(new ListJobs(target)));
	}

	@Override
	public JobExecutionList listJobExecutions() throws PlatformLayerClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public JobData doAction(PlatformLayerKey key, Action action) throws PlatformLayerClientException {
		MappedPlatformLayerKey mapped = mapToChild(key);
		JobData result = mapped.child.client.doAction(mapped.key, action);
		return mapped.child.setHost(result);
	}

	@Override
	public JobData doAction(PlatformLayerKey key, String action, Format dataFormat) throws PlatformLayerClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Tags changeTags(PlatformLayerKey key, TagChanges tagChanges, Long ifVersion)
			throws PlatformLayerClientException {
		MappedPlatformLayerKey mapped = mapToChild(key);
		return mapped.child.client.changeTags(mapped.key, tagChanges, ifVersion);
	}

	@Override
	public MetricDataStream getMetric(MetricQuery query) throws PlatformLayerClientException {
		MappedPlatformLayerKey mapped = mapToChild(query.item);
		MetricQuery mappedQuery = query.copy();
		mappedQuery.item = mapped.key;
		return mapped.child.client.getMetric(mappedQuery);
	}

	@Override
	public MetricInfoCollection listMetrics(PlatformLayerKey key) throws PlatformLayerClientException {
		MappedPlatformLayerKey mapped = mapToChild(key);
		return mapped.child.client.listMetrics(mapped.key);
	}

	Collection<ServiceInfo> servicesCache;

	@Override
	public Collection<ServiceInfo> listServices(boolean allowCache) throws PlatformLayerClientException {
		Collection<ServiceInfo> returnValue = servicesCache;
		if (!allowCache || returnValue == null) {
			Map<String, ServiceInfo> services = Maps.newHashMap();

			// We have to duplicate the results
			// TODO: Do we need to be smarter about how we dedup?
			for (ServiceInfo service : doListConcatenation(getChildClients(), new ListServices(allowCache))) {
				services.put(service.namespace, service);
			}

			returnValue = services.values();
			servicesCache = Lists.newArrayList(returnValue);
		}
		return returnValue;
	}

	@Override
	public String activateService(String serviceType, String data, Format format) throws PlatformLayerClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getActivation(String serviceType, Format format) throws PlatformLayerClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getSshPublicKey(String serviceType) throws PlatformLayerClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getSchema(String serviceType, Format format) throws PlatformLayerClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void ensureLoggedIn() throws PlatformLayerAuthenticationException {
		// throw new UnsupportedOperationException();
	}

	@Override
	protected PlatformLayerKey toKey(JaxbHelper jaxbHelper) throws PlatformLayerClientException {
		PlatformLayerKey key = super.toKey(jaxbHelper);
		// TODO: Add host
		return key;
	}

	protected <T> PlatformLayerKey toKey(T item) throws PlatformLayerClientException {
		if (item instanceof ItemBase) {
			ItemBase itemBase = (ItemBase) item;
			return itemBase.getKey();
		} else {
			throw new UnsupportedOperationException();
		}
	}

	protected PlatformLayerKey toKey(Class<?> c) throws PlatformLayerClientException {
		JaxbHelper jaxbHelper = toJaxbHelper(c, new Class[0]);
		return toKey(jaxbHelper);
	}

	private MappedPlatformLayerKey mapToChildForCreate(PlatformLayerKey plk) {
		if (plk.getItemId() != null) {
			throw new IllegalArgumentException();
		}

		FederationMapping childKey = federationMap.getClientForCreate(plk);
		ManagedItemId childItemId;

		ChildClient childClient = getClient(childKey);
		childItemId = null;

		MappedPlatformLayerKey mapped = new MappedPlatformLayerKey();
		mapped.child = childClient;
		mapped.key = new PlatformLayerKey(childKey.host, childKey.project, plk.getServiceType(), plk.getItemType(),
				childItemId);
		return mapped;
	}

	private MappedPlatformLayerKey mapToChildForPut(PlatformLayerKey plk) {
		FederationMapping childKey = federationMap.getClientForCreate(plk);
		ManagedItemId childItemId = plk.getItemId();

		ChildClient childClient = getClient(childKey);

		MappedPlatformLayerKey mapped = new MappedPlatformLayerKey();
		mapped.child = childClient;
		mapped.key = new PlatformLayerKey(childKey.host, childKey.project, plk.getServiceType(), plk.getItemType(),
				childItemId);
		return mapped;
	}

	private MappedPlatformLayerKey mapToChild(PlatformLayerKey plk) {
		// if (plk.getHost() != null) {
		//
		// }

		ManagedItemId itemId = plk.getItemId();
		if (itemId == null || itemId.isEmpty()) {
			throw new IllegalArgumentException();
		}

		FederationKey host = plk.getHost();
		if (host == null) {
			host = FederationKey.LOCAL;
		}

		ProjectId project = plk.getProject();
		if (project == null) {
			project = defaultProject;
			// project = federationMap.getLocalClient().getProject();
		}

		ChildClient childClient = getClient(new FederationMapping(host, project));

		MappedPlatformLayerKey mapped = new MappedPlatformLayerKey();
		mapped.child = childClient;

		mapped.key = new PlatformLayerKey(host, project, plk.getServiceType(), plk.getItemType(), plk.getItemId());

		return mapped;

		// Iterable<ChildClient> childClients = getChildClients(plk);

		// ChildClient client = null;
		// for (ChildClient childClient : childClients) {
		// if (client == null) {
		// client = childClient;
		// } else {
		// throw new IllegalStateException("Multiple clients found");
		// }
		// }
		// return client;
	}

	private Iterable<ChildClient> getChildClients() {
		return childClients.values();
	}

	private Iterable<ChildClient> getChildClients(PlatformLayerKey path) {
		List<ChildClient> clients = Lists.newArrayList();

		for (FederationMapping key : federationMap.getClients(path)) {
			ChildClient child = getClient(key);
			clients.add(child);
		}

		return clients;
	}

	private ChildClient getClient(FederationMapping key) {
		if (key.project == null) {
			key = new FederationMapping(key.host, getProject());
		}

		if (key.host == null) {
			key = new FederationMapping(FederationKey.LOCAL, key.project);
		}

		ChildClient child = this.childClients.get(key);
		if (child == null) {
			throw new IllegalStateException();
		}
		return child;
	}

	private <T> T oneOrNull(Iterable<T> iterable) throws PlatformLayerClientException {
		T item = null;
		int count = 0;
		for (T i : iterable) {
			if (count == 0) {
				item = i;
			} else {
				throw new PlatformLayerClientException("Expected exactly one matching item");
			}
		}
		return item;
	}

	// public static PlatformLayerClient build(TypedPlatformLayerClient localClient, TypedItemMapper mapper)
	// throws OpsException {
	// FederationMap federationMap = buildFederationMap(localClient, mapper);
	//
	// ForkJoinStrategy forkJoinPool = new FakeForkJoinStrategy();
	//
	// return new FederatedPlatformLayerClient(federationMap, forkJoinPool);
	// }

	public static PlatformLayerClient build(ProjectId defaultProject, FederationMap federationMap) throws OpsException {
		ForkJoinStrategy forkJoinPool = new FakeForkJoinStrategy();

		TypedItemMapper mapper = null;
		return new FederatedPlatformLayerClient(mapper, defaultProject, federationMap, forkJoinPool);
	}

	// public static FederationMap buildFederationMap(HttpStrategy httpStrategy, TypedPlatformLayerClient localClient,
	// TypedItemMapper mapper) throws OpsException {
	// FederationConfiguration federationMapConfig = buildFederationConfiguration(localClient);
	//
	// FederationMap federationMap = new FederationMap(httpStrategy, mapper, federationMapConfig);
	//
	// if (localClient != null) {
	// federationMap.addDefault(localClient);
	// }
	//
	// return federationMap;
	// }

	public static FederationConfiguration buildFederationConfiguration(TypedPlatformLayerClient localClient)
			throws OpsException {
		FederationConfiguration federationMapConfig = new FederationConfiguration();

		String federationNamespace = "http://platformlayer.org/service/federation/v1.0";
		boolean federationEnabled = isServiceEnabled(localClient, federationNamespace);

		if (federationEnabled) {
			for (FederatedService service : localClient.listItems(FederatedService.class)) {
				PlatformLayerConnectionConfiguration config = new PlatformLayerConnectionConfiguration();
				config.key = service.getKey();
				config.secret = service.getSecret();
				config.authenticationEndpoint = service.getServer();
				config.tenant = service.getTenant();
				config.username = service.getUsername();
				config.platformlayerEndpoint = service.getServer();

				federationMapConfig.systems.add(config);
			}

			for (FederatedServiceMap map : localClient.listItems(FederatedServiceMap.class)) {
				FederationRule rule = new FederationRule();
				rule.target = map.getTarget();
				rule.serviceType = map.getServiceType();

				federationMapConfig.rules.add(rule);
			}
		}

		return federationMapConfig;
	}

	private static boolean isServiceEnabled(TypedPlatformLayerClient localClient, String namespace)
			throws PlatformLayerClientException {
		Collection<ServiceInfo> services = localClient.listServices(true);
		boolean found = false;
		for (ServiceInfo service : services) {
			if (namespace.equals(service.getNamespace())) {
				found = true;
			}
		}
		return found;
	}

	@Override
	public UntypedItem putItemByTag(PlatformLayerKey key, Tag uniqueTag, String data, Format format)
			throws PlatformLayerClientException {
		MappedPlatformLayerKey mapped = mapToChildForPut(key);

		UntypedItemXml post = UntypedItemXml.build(data);
		post.setPlatformLayerKey(mapped.key);

		UntypedItem item = mapped.child.client.putItemByTag(mapped.key, uniqueTag, post.serialize(), format);
		return mapped.child.setHost(item);
	}

	@Override
	public UntypedItemCollection listChildren(PlatformLayerKey parent, boolean includeDeleted)
			throws PlatformLayerClientException {
		return doListConcatenationUntyped(getChildClients(parent),
				AddHostUntyped.wrap(new ListChildren(parent, includeDeleted)));
	}

	@Override
	public ProjectId getProject() {
		return defaultProject; // federationMap.getLocalClient().getProject();
	}

	@Override
	public PlatformLayerEndpointInfo getEndpointInfo(PlatformLayerKey plk) {
		MappedPlatformLayerKey mapped = mapToChild(plk);

		return mapped.child.client.getEndpointInfo(mapped.key);

	}

	@Override
	public JobLog getJobExecutionLog(String jobId, String executionId) throws PlatformLayerClientException {
		throw new UnsupportedOperationException();
	}

	@Override
	public JobExecutionList listJobExecutions(String jobId) throws PlatformLayerClientException {
		throw new UnsupportedOperationException();
		// assert false; // This logic is suspect...
		// MappedPlatformLayerKey mapped = mapToChild(jobKey);
		// return mapped.child.client.listJobExecutions(jobKey);
	}

	@Override
	public <T extends ItemBase> T putItem(T item) throws OpsException {
		throw new UnsupportedOperationException();
	}

}
