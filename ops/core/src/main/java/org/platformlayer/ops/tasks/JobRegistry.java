package org.platformlayer.ops.tasks;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.platformlayer.RepositoryException;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.ids.ProjectId;
import org.platformlayer.model.ProjectAuthorization;
import org.platformlayer.ops.OperationType;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsSystem;
import org.platformlayer.xaas.repository.JobRepository;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Singleton
public class JobRegistry {
	@Inject
	OpsSystem opsSystem;

	@Inject
	JobRepository repository;

	final Map<PlatformLayerKey, JobRecord> activeJobs = Maps.newHashMap();

	final LinkedList<JobRecord> recentJobs = Lists.newLinkedList();

	@Inject
	JobGraph jobGraph;

	@Inject
	OpsContextBuilder opsContextBuilder;

	public PlatformLayerKey enqueueOperation(OperationType operationType, ProjectAuthorization auth,
			PlatformLayerKey targetItem) {
		JobKey key = new JobKey(targetItem, operationType);

		ProjectId projectId;
		try {
			projectId = opsContextBuilder.getRunAsProjectId(auth);
		} catch (OpsException e) {
			throw new IllegalStateException("Error getting projectId", e);
		}

		JobRecord jobRecord = new JobRecord(key, targetItem.getServiceType(), auth, projectId);

		return jobGraph.trigger(jobRecord);
	}

	private static final int RECENT_JOB_COUNT = 100;

	public List<JobRecord> getActiveJobs(ProjectId projectId) {
		List<JobRecord> jobs = Lists.newArrayList();
		synchronized (activeJobs) {
			for (JobRecord job : activeJobs.values()) {
				if (!Objects.equal(job.getJobKey().getProject(), projectId)) {
					continue;
				}
				jobs.add(job);
			}
		}
		synchronized (recentJobs) {
			for (JobRecord job : recentJobs) {
				if (!job.getJobKey().getProject().equals(projectId)) {
					continue;
				}
				jobs.add(job);
			}
		}
		return jobs;
	}

	void recordJobEnd(JobRecord record) throws OpsException {
		PlatformLayerKey key = record.getJobData().key;

		synchronized (activeJobs) {
			activeJobs.remove(key);
		}

		synchronized (recentJobs) {
			recentJobs.push(record);
			if (recentJobs.size() > RECENT_JOB_COUNT) {
				recentJobs.pop();
			}
		}

		try {
			repository.recordJob(key, record.getTargetItemKey(), record.getJobData().state, record.getLog());
		} catch (RepositoryException e) {
			throw new OpsException("Error writing job to repository", e);
		}
	}

	JobRecord startJob(JobRecord jobRecord) {
		PlatformLayerKey jobKey = jobRecord.getJobKey();

		if (jobKey != null) {
			synchronized (activeJobs) {
				activeJobs.put(jobKey, jobRecord);
			}
		}

		return jobRecord;
	}

	public JobRecord getJob(PlatformLayerKey jobKey, boolean fetchLog) {
		JobRecord jobRecord = null;
		synchronized (activeJobs) {
			jobRecord = activeJobs.get(jobKey);
		}

		if (jobRecord == null) {
			synchronized (recentJobs) {
				for (JobRecord recentJob : recentJobs) {
					if (recentJob.getJobData().key.equals(jobKey)) {
						jobRecord = recentJob;
						break;
					}
				}
			}
		}

		if (jobRecord == null) {
			throw new UnsupportedOperationException();

			// jobRecord = repository.getJob(jobId, fetchLog);
		}

		return jobRecord;
	}

}
