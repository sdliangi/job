package com.xxl.job.admin.core.model;

import java.util.Date;
import java.util.List;

/**
 * 任务链拓扑信息
 */
public class JobTopology {

    private int jobId;  //任务id
    private XxlJobInfo xxlJobInfo;  //任务信息
    private int jobGroup;    //执行器id
    private long logId;  //日志id
    private int jobStatus;  //0未开始,1成功，2调度失败，3执行失败， 4执行中
    private Date triggerTime;
    private Date endTime;
    private List<JobTopology> childJobTopology;    //子任务

    public XxlJobInfo getXxlJobInfo() {
        return xxlJobInfo;
    }

    public void setXxlJobInfo(XxlJobInfo xxlJobInfo) {
        this.xxlJobInfo = xxlJobInfo;
    }

    public Date getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(Date triggerTime) {
        this.triggerTime = triggerTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public int getJobGroup() {
        return jobGroup;
    }

    public void setJobGroup(int jobGroup) {
        this.jobGroup = jobGroup;
    }

    public long getLogId() {
        return logId;
    }

    public void setLogId(long logId) {
        this.logId = logId;
    }

    public int getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(int jobStatus) {
        this.jobStatus = jobStatus;
    }

    public List<JobTopology> getChildJobTopology() {
        return childJobTopology;
    }

    public void setChildJobTopology(List<JobTopology> childJobTopology) {
        this.childJobTopology = childJobTopology;
    }
}
