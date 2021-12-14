package com.xxl.job.admin.service;

import com.xxl.job.admin.core.model.JobTopology;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.core.biz.model.ReturnT;

import java.util.List;

public interface JobTopologyApiService {
    List<XxlJobLog> getJobTopologyList(int start, int length, int jobGroup, int jobId, int status, String filterTime);

    JobTopology getTopologyJobChain(int jobId);

    ReturnT addAfterPreJob(int preJobId, int childJobId);

    ReturnT disconnect(int preJobId, int behindJobId);

    ReturnT deleteJobChain(int jobId);

    JobTopology getJobTopologyInfo(long masterLogId);
}
