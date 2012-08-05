package org.platformlayer.gwt.client;

import org.platformlayer.gwt.client.accountsummary.AccountSummaryActivity;
import org.platformlayer.gwt.client.addcreditcard.AddCreditCardActivity;
import org.platformlayer.gwt.client.breadcrumb.HeaderActivity;
import org.platformlayer.gwt.client.breadcrumb.HeaderActivityMapper;
import org.platformlayer.gwt.client.breadcrumb.HeaderView;
import org.platformlayer.gwt.client.home.HomeActivity;
import org.platformlayer.gwt.client.item.ItemActivity;
import org.platformlayer.gwt.client.itemlist.ItemListActivity;
import org.platformlayer.gwt.client.job.JobActivity;
import org.platformlayer.gwt.client.joblist.JobListActivity;
import org.platformlayer.gwt.client.project.ProjectActivity;
import org.platformlayer.gwt.client.projectlist.ProjectListActivity;
import org.platformlayer.gwt.client.signin.SignInActivity;
import org.platformlayer.gwt.client.signup.SignUpActivity;

import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;
import com.google.gwt.place.shared.PlaceController;
import com.google.gwt.place.shared.PlaceHistoryMapper;
import com.google.web.bindery.event.shared.EventBus;

@GinModules(ApplicationGinModule.class)
public interface ApplicationGinjector extends Ginjector {
	EventBus getEventBus();

	PlaceController getPlaceController();

	ContentActivityMapper getContentActivityMapper();

	HeaderActivityMapper getBreadcrumbActivityMapper();

	HeaderView getHeaderView();

	SignInActivity getLoginActivity();

	HomeActivity getHomeActivity();

	HeaderActivity getHeaderActivity();

	ProjectListActivity getProjectListActivity();

	ItemListActivity getItemListActivity();

	JobListActivity getJobListActivity();

	ProjectActivity getProjectActivity();

	JobActivity getJobActivity();

	PlaceHistoryMapper getPlaceHistoryMapper();

	ItemActivity getItemActivity();

	AccountSummaryActivity getAccountSummaryActivity();

	AddCreditCardActivity getAddCreditCardActivity();

	SignUpActivity getSignUpActivity();
}
