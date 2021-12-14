package com.xxl.job.admin.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.xxl.job.admin.core.model.JobTopology;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.util.StringUtils;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobLogDao;
import com.xxl.job.admin.service.JobTopologyApiService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class JobTopologyApiServiceImpl implements JobTopologyApiService {
    private static Logger logger = LoggerFactory.getLogger(JobTopologyApiServiceImpl.class);

    @Resource
    public XxlJobInfoDao xxlJobInfoDao;
    @Resource
    public XxlJobLogDao xxlJobLogDao;
    @Autowired
    private XxlJobServiceImpl xxlJobService;

    @Override
    public List<XxlJobLog> getJobTopologyList(int start, int length, int jobGroup, int jobId, int status, String filterTime) {
        List<JobTopology> result = new ArrayList<>();
        Date triggerTimeStart = null;
        Date triggerTimeEnd = null;
        if (filterTime != null && filterTime.trim().length() > 0) {
            String[] temp = filterTime.split(" - ");
            if (temp.length == 2) {
                triggerTimeStart = DateUtil.parseDateTime(temp[0]);
                triggerTimeEnd = DateUtil.parseDateTime(temp[1]);
            }
        }
        //查询
        List<XxlJobLog> xxlJobLogs = xxlJobLogDao.pageMasterLogList(start, length, jobGroup, jobId, triggerTimeStart, triggerTimeEnd, status);
        //解析
//        for (XxlJobLog jobLog : xxlJobLogs) {
//            String taskBatchId = jobLog.getTaskBatchId();
//            Map<Integer, XxlJobLog> batchLogMaps;
//            if (!StringUtils.isEmpty(taskBatchId)) {
//                String[] split = taskBatchId.split(":");
//                int firstJobId = Integer.parseInt(split[0]);
//                //同批次日志
//                batchLogMaps = xxlJobLogDao.getJobLogByTaskBatchId(taskBatchId);
//
//                JobTopology jobTopology = getJobTopology(firstJobId, batchLogMaps);
//                result.add(jobTopology);
//            } else {
//                //主节点任务失败
//                batchLogMaps = new HashMap<>();
//                batchLogMaps.put(jobLog.getJobId(), jobLog);
//                JobTopology jobTopology = getJobTopology(jobLog.getJobId(), batchLogMaps);
//                result.add(jobTopology);
//            }
//        }
        return xxlJobLogs;
    }

    @Override
    public JobTopology getTopologyJobChain(int jobId) {
        XxlJobInfo xxlJobInfo = xxlJobInfoDao.loadById(jobId);
        if (xxlJobInfo == null || xxlJobInfo.getIsMasterJob() != 1) {
            return null;
        }
        return getJobTopology(jobId);
    }

    @Override
    public ReturnT addAfterPreJob(int preJobId, int childJobId) {
        XxlJobInfo childJob = xxlJobInfoDao.loadById(childJobId);
        if (childJob == null) {
            return new ReturnT(ReturnT.FAIL_CODE, "子任务不存在");
        }
        //判断此次添加的子任务链中的任务是否合法
        ReturnT returnT = xxlJobService.validChildJob(childJobId, preJobId);
        if (returnT.getCode() != 200) {
            return returnT;
        }
        //更新父任务的引用
        String childJobIds = xxlJobInfoDao.getChildJobIdById(preJobId);
        if (StringUtils.isEmpty(childJobIds)) {
            xxlJobInfoDao.updateChildIdById(childJobId + "", preJobId);
        } else {
            childJobIds = childJobIds + "," + childJobId;
            xxlJobInfoDao.updateChildIdById(childJobIds, preJobId);
        }
        return ReturnT.SUCCESS;
    }

    /**
     * 清除指定父任务绑定的子任务
     *
     * @param preJobId    父任务id
     * @param behindJobId 子任务id
     * @return
     */
    @Override
    public ReturnT disconnect(int preJobId, int behindJobId) {
        String childJobId = xxlJobInfoDao.getChildJobIdById(preJobId);
        if (isNumeric(childJobId)) {
            xxlJobInfoDao.updateChildIdById(null, preJobId);
        } else if (childJobId.contains(behindJobId + ",")) {
            childJobId = childJobId.replaceFirst(behindJobId + ",", "");
            xxlJobInfoDao.updateChildIdById(childJobId, preJobId);
        } else if (childJobId.contains("," + behindJobId)) {
            childJobId = childJobId.replaceFirst("," + behindJobId, "");
            xxlJobInfoDao.updateChildIdById(childJobId, preJobId);
        } else {
            return ReturnT.FAIL;
        }

        return ReturnT.SUCCESS;
    }

    @Override
    public ReturnT deleteJobChain(int jobId) {
        XxlJobInfo xxlJobInfo = xxlJobInfoDao.loadById(jobId);
        if (xxlJobInfo.getIsMasterJob() != 1) {
            //解除jobId的父引用
            List<XxlJobInfo> preJob = xxlJobInfoDao.getPreJobByChildId(jobId);
            if (preJob.size() > 1 || preJob.size() == 0 || preJob == null) {
                logger.error("id：{}存在多个父任务节点或者不存在父节点", jobId);
                return ReturnT.FAIL;
            }
            ReturnT disconnect = disconnect(preJob.get(0).getId(), jobId);
            if (disconnect.getCode() == 200) {
                return deleteJobChainRecursive(jobId);
            }
            return disconnect;
        }
        //主节点
        return deleteJobChainRecursive(jobId);
    }

    @Override
    public JobTopology getJobTopologyInfo(long masterLogId) {
        XxlJobLog jobLog = xxlJobLogDao.load(masterLogId);
        String taskBatchId = jobLog.getTaskBatchId();
        Map<Integer, XxlJobLog> batchLogMaps;
        if (!StringUtils.isEmpty(taskBatchId)) {
            String[] split = taskBatchId.split(":");
            int firstJobId = Integer.parseInt(split[0]);
            //同批次日志
            batchLogMaps = xxlJobLogDao.getJobLogByTaskBatchId(taskBatchId);

            JobTopology jobTopology = getJobTopology(firstJobId, batchLogMaps);
            return jobTopology;
        } else {
            //主节点任务失败
            batchLogMaps = new HashMap<>();
            batchLogMaps.put(jobLog.getJobId(), jobLog);
            JobTopology jobTopology = getJobTopology(jobLog.getJobId(), batchLogMaps);
            return jobTopology;
        }
    }


    private ReturnT deleteJobChainRecursive(int jobId) {
        String childJobIds = xxlJobInfoDao.getChildJobIdById(jobId);
        xxlJobInfoDao.delete(jobId);
        if (!StringUtils.isEmpty(childJobIds)) {
            for (String id : childJobIds.split(",")) {
                return deleteJobChainRecursive(Integer.parseInt(id));
            }
        }
        return ReturnT.SUCCESS;
    }


    private boolean isNumeric(String str) {
        try {
            int result = Integer.valueOf(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 得到任务链
     *
     * @param jobId
     * @return
     */
    private JobTopology getJobTopology(int jobId) {
        XxlJobInfo xxlJobInfo = xxlJobInfoDao.loadById(jobId);
        String childJobIds = xxlJobInfo.getChildJobId();
        JobTopology jobTopology = new JobTopology();
        jobTopology.setJobId(jobId);
        jobTopology.setXxlJobInfo(xxlJobInfoDao.loadById(jobId));
        jobTopology.setJobGroup(xxlJobInfo.getJobGroup());

        if (!StringUtils.isEmpty(childJobIds)) {
            List<JobTopology> jobTopologies = new ArrayList<>();
            if (childJobIds.contains(",")) {
                //多个子任务
                for (String childJobId : childJobIds.split(",")) {
                    jobTopologies.add(getJobTopology(Integer.parseInt(childJobId)));
                }
            } else {
                //一个子任务
                jobTopologies.add(getJobTopology(Integer.parseInt(childJobIds)));
            }
            jobTopology.setChildJobTopology(jobTopologies);
        }

        return jobTopology;

    }

    /**
     * 递归求取批次拓扑执行详细信息对象
     *
     * @param jobId
     * @param batchLogMaps
     * @return
     */
    private JobTopology getJobTopology(int jobId, Map<Integer, XxlJobLog> batchLogMaps) {
        String childJobIds = xxlJobInfoDao.getChildJobIdById(jobId);
        JobTopology jobTopology = new JobTopology();
//        XxlJobLog xxlJobLog = batchLogMaps.get(jobId);    //报错java.util.HashMap cannot be cast to com.xxl.job.admin.core.model.XxlJobLog
        //转为json中转
        XxlJobLog xxlJobLog = JSONObject.parseObject(JSONObject.toJSONString(batchLogMaps.get(jobId)), XxlJobLog.class);
        if (xxlJobLog == null) {
            //子任务未执行
            return null;
        }
        jobTopology.setJobId(xxlJobLog.getJobId());
        jobTopology.setJobGroup(xxlJobLog.getJobGroup());
        jobTopology.setLogId(xxlJobLog.getId());
        jobTopology.setTriggerTime(xxlJobLog.getTriggerTime());
        jobTopology.setEndTime(xxlJobLog.getHandleTime());
        if (xxlJobLog.getHandleCode() == 200) {
            jobTopology.setJobStatus(1);    //success
        } else if (xxlJobLog.getTriggerCode() == 200 && xxlJobLog.getHandleCode() == 0) {
            jobTopology.setJobStatus(4);    //执行中
        } else if (xxlJobLog.getTriggerCode() != 200 && (xxlJobLog.getTriggerCode() != 0)) {
            jobTopology.setJobStatus(2);    //调度失败
        } else if (xxlJobLog.getTriggerCode() == 200 && xxlJobLog.getHandleCode() != 0 && xxlJobLog.getHandleCode() != 200) {
            jobTopology.setJobStatus(3);    //执行失败
        }

        if (!StringUtils.isEmpty(childJobIds)) {
            List<JobTopology> jobTopologies = new ArrayList<>();
            if (childJobIds.contains(",")) {
                //多个子任务
                for (String childJobId : childJobIds.split(",")) {
                    jobTopologies.add(getJobTopology(Integer.parseInt(childJobId), batchLogMaps));
                }
            } else {
                //一个子任务
                jobTopologies.add(getJobTopology(Integer.parseInt(childJobIds), batchLogMaps));
            }
            jobTopology.setChildJobTopology(jobTopologies);
        }

        return jobTopology;
    }

}
