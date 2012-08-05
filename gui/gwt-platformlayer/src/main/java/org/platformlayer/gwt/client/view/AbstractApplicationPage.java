package org.platformlayer.gwt.client.view;

import java.util.List;

import org.platformlayer.gwt.client.widgets.Repeater;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

public abstract class AbstractApplicationPage extends Composite implements ApplicationView {
	@Override
	public Widget asWidget() {
		return this;
	}

	protected static <T> void addDataDisplay(CellList<T> list, ListDataProvider<T> provider) {
		if (!provider.getDataDisplays().contains(list)) {
			provider.addDataDisplay(list);
		}
	}

	protected static <T> void addDataDisplay(CellList<T> list, List<T> values) {
		addDataDisplay(list, new ListDataProvider<T>(values));
	}

	protected static <T> void addDataDisplay(Repeater<T> repeater, Iterable<T> items) {
		repeater.replaceAllChildren(items);
	}

	private void addClickHandler(Element button, EventListener eventListener) {
		DOM.setEventListener(button.<com.google.gwt.user.client.Element> cast(), eventListener);
		DOM.sinkEvents(button.<com.google.gwt.user.client.Element> cast(), Event.ONCLICK);
	}

	protected void addClickHandler(Element button, final ClickHandler clickHandler) {
		addClickHandler(button, new EventListener() {

			@Override
			public void onBrowserEvent(Event event) {
				event.preventDefault();

				ClickEvent clickEvent = null; // Is this OK?
				clickHandler.onClick(clickEvent);
			}

		});
	}

}