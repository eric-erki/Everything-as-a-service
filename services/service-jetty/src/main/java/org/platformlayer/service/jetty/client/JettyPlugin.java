package org.platformlayer.service.jetty.client;

import javax.inject.Provider;
import javax.inject.Singleton;

import org.platformlayer.gwt.client.ExternalPlugin;
import org.platformlayer.gwt.client.places.ShellPlace;
import org.platformlayer.service.jetty.client.home.HomePlace;
import org.platformlayer.service.jetty.client.jettyservice.JettyServiceActivity;
import org.platformlayer.service.jetty.client.jettyservice.JettyServicePlace;
import org.platformlayer.service.jetty.client.jettyservicelist.JettyServiceListActivity;
import org.platformlayer.service.jetty.client.jettyservicelist.JettyServiceListPlace;
import org.platformlayer.ui.shared.client.plugin.PlugInView;
import org.platformlayer.ui.shared.client.plugin.PluginItem;

import com.google.gwt.activity.shared.Activity;
import com.google.gwt.place.shared.Place;
import com.google.inject.Inject;

@Singleton
public class JettyPlugin extends ExternalPlugin {
	public static final String KEY = "jetty";

	public static final String SERVICE_TYPE = "jetty";
	public static final String ITEM_TYPE_JETTYSERVICE = "jettyService";

	public JettyPlugin() {
		super("Jetty", KEY);

		addItem(new PluginItem(ITEM_TYPE_JETTYSERVICE, "Jetty Services", JettyServiceListPlace.KEY));
	}

	static JettyPlugin INSTANCE;

	@Override
	public void register() {
		if (INSTANCE != null) {
			throw new IllegalStateException();
		}
		INSTANCE = this;

		super.register();
	}

	@Override
	public PlugInView getWidget(String specifier) {
		// if (specifier.equals(PlugInProvider.HOME)) {
		// return homeButton;
		// }

		return null;
	}

	@Override
	public HomePlace getRootPlace(Place parent) {
		HomePlace root = new HomePlace((ShellPlace) parent);
		return root;
	}

	public static JettyPlugin get() {
		return INSTANCE;
	}

	@Inject
	Provider<JettyServiceListActivity> jettyServiceListActivity;

	@Inject
	Provider<JettyServiceActivity> jettyServiceActivity;

	@Override
	public Activity getActivity(Place place, String context) {
		if (place instanceof JettyServiceListPlace) {
			return jettyServiceListActivity.get();
		}
		// if (place instanceof HomePlace) {
		// return homeActivity.get();
		// }
		if (place instanceof JettyServicePlace) {
			return jettyServiceActivity.get();
		}
		return null;
	}

}
