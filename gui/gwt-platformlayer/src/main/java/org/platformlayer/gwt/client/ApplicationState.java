package org.platformlayer.gwt.client;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.platformlayer.gwt.client.api.login.Authentication;
import org.platformlayer.gwt.client.api.platformlayer.OpsProject;
import org.platformlayer.gwt.client.places.ApplicationPlace;
import org.platformlayer.gwt.client.project.ProjectPlace;
import org.platformlayer.gwt.client.stores.JobStore;

import com.google.gwt.place.shared.PlaceController;
import com.google.gwt.view.client.ListDataProvider;

@Singleton
public class ApplicationState {
	@Inject
	PlaceController placeController;

	@Inject
	JobStore jobStore;

	private Authentication authentication;

	private final ListDataProvider<OpsProject> projectsProvider = new ListDataProvider<OpsProject>();

	public Authentication getAuthentication() {
		return authentication;
	}

	public void setAuthentication(Authentication authentication, List<String> projects) {
		this.authentication = authentication;

		String platformlayerUrl = getPlatformLayerBaseUrl();

		List<OpsProject> projectList = getProjects();
		projectList.clear();
		for (String project : projects) {
			projectList.add(new OpsProject(platformlayerUrl, project, authentication));
		}
	}

	public OpsProject findProject(String projectKey) {
		for (OpsProject project : getProjects()) {
			if (project.getProjectName().equals(projectKey)) {
				return project;
			}
		}

		return null;
	}

	private String getPlatformLayerBaseUrl() {
		// TODO: Get from GWT?? Get from auth??
		return "https://dev.platformlayer.net:8082/v0/";
	}

	public String getAuthBaseUrl() {
		String base = getPlatformLayerBaseUrl();
		if (base.equals("https://dev.platformlayer.net:8082/v0/")) {
			return "https://dev.platformlayer.net:5001/v2.0/";
		}
		return "https://auth.platformlayer.net:5001/v2.0/";
	}

	public ListDataProvider<OpsProject> getProjectsProvider() {
		// roomsProvider = new ListDataProvider<Room>(rooms);
		return projectsProvider;
	}

	public List<OpsProject> getProjects() {
		return projectsProvider.getList();
	}

	public JobStore getJobStore() {
		return jobStore;
	}

	public OpsProject findProject(ApplicationPlace place) {
		ProjectPlace project = place.getProject();
		if (project == null) {
			return null;
		}

		return findProject(project.getProjectKey());
	}
}
