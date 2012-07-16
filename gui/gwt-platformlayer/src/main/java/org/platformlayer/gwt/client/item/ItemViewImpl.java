package org.platformlayer.gwt.client.item;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.platformlayer.gwt.client.api.platformlayer.UntypedItem;
import org.platformlayer.gwt.client.view.AbstractApplicationPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;

public class ItemViewImpl extends AbstractApplicationPage implements ItemView {
	static final Logger log = Logger.getLogger(ItemViewImpl.class.getName());

	interface ViewUiBinder extends UiBinder<HTMLPanel, ItemViewImpl> {
	}

	private static ViewUiBinder viewUiBinder = GWT.create(ViewUiBinder.class);

	public ItemViewImpl() {
		initWidget(viewUiBinder.createAndBindUi(this));
	}

	private ItemActivity activity;

	@UiField
	SpanElement labelSpan;

	@UiField
	Button editButton;

	UntypedItem untypedModel;

	@Override
	public void start(ItemActivity activity) {
		this.activity = activity;

		untypedModel = null;
		updateModelUi();

		SafeHtml html = SafeHtmlUtils.fromString(activity.getItemPath());
		labelSpan.setInnerSafeHtml(html);

		activity.getItem(new AsyncCallback<UntypedItem>() {

			@Override
			public void onSuccess(UntypedItem result) {
				untypedModel = result;
				updateModelUi();
			}

			@Override
			public void onFailure(Throwable caught) {
				log.log(Level.SEVERE, "Error retrieving item", caught);
			}
		});
	}

	protected void updateModelUi() {
		boolean haveModel = untypedModel != null;

		editButton.setEnabled(haveModel);
	}

	@UiHandler("editButton")
	public void onEditButtonClick(ClickEvent e) {
		if (untypedModel == null)
			return;

		EditItemDialog dialog = new EditItemDialog();
		dialog.start(untypedModel);
	}
}