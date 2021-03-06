package org.platformlayer.service.dns.client.dnsrecord;

import javax.inject.Singleton;

import org.platformlayer.dns.model.DnsRecord;
import org.platformlayer.gwt.client.ui.ItemViewImpl;
import org.platformlayer.gwt.client.ui.ViewHandler;
import org.platformlayer.gwt.client.widgets.ControlGroup;
import org.platformlayer.gwt.client.widgets.Form;

import com.google.gwt.core.client.GWT;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorDriver;
import com.google.gwt.editor.client.SimpleBeanEditorDriver;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;

@Singleton
public class DnsRecordViewImpl extends ItemViewImpl<DnsRecord> implements DnsRecordView, Editor<DnsRecord> {

	interface ViewUiBinder extends UiBinder<HTMLPanel, DnsRecordViewImpl> {
	}

	private static ViewUiBinder viewUiBinder = GWT.create(ViewUiBinder.class);

	interface Driver extends SimpleBeanEditorDriver<DnsRecord, DnsRecordViewImpl> {
	}

	Driver driver = GWT.create(Driver.class);

	public DnsRecordViewImpl() {
		initWidget(viewUiBinder.createAndBindUi(this));

		driver.initialize(this);
	}

	@UiField
	Form form;

	@UiField
	ControlGroup dnsName;
	@UiField
	ControlGroup recordType;

	private DnsRecord model;

	@Override
	public void start(ViewHandler activity) {
		super.start(activity);

		form.clearAlerts();

		driver.edit(null);
	}

	@Override
	public void editItem(DnsRecord model) {
		this.model = model;
		driver.edit(model);
	}

	@Override
	protected Form getForm() {
		return form;
	}

	@Override
	public String getViewTitle() {
		return DnsRecordPlace.LABEL;
	}

	@Override
	protected EditorDriver<DnsRecord> getDriver() {
		return driver;
	}

}