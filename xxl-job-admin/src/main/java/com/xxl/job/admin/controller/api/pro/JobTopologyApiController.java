package com.xxl.job.admin.controller.api.pro;

import com.xxl.job.admin.core.model.JobTopology;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.service.JobTopologyApiService;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/topology")
public class JobTopologyApiController {
    @Autowired
    private JobTopologyApiService jobTopologyApiService;
    @Autowired
    private XxlJobInfoDao xxlJobInfoDao;

    @GetMapping("/masterJobList")
    public ReturnT masterJobList(@RequestParam(required = false, defaultValue = "0") int start,
                                 @RequestParam(required = false, defaultValue = "10") int length,
                                 int jobGroup, int triggerStatus, String jobDesc, String executorHandler, String author) {
        List<XxlJobInfo> list = xxlJobInfoDao.pageMasterJobList(start, length, jobGroup, triggerStatus, jobDesc, executorHandler, author);
        return new ReturnT(list);
    }


    @GetMapping("/detail")
    public ReturnT getJobTopologyInfo(long masterLogId) {
        JobTopology jobTopology = jobTopologyApiService.getJobTopologyInfo(masterLogId);
        return new ReturnT(jobTopology);
    }


    /**
     * @param start
     * @param length
     * @param jobGroup
     * @param jobId
     * @param status     0 全部 1成功 2失败
     * @param filterTime
     * @return
     */
    @GetMapping("/topologyList")
    public ReturnT getJobTopologyList(@RequestParam(required = false, defaultValue = "0") int start,
                                      @RequestParam(required = false, defaultValue = "3") int length,
                                      int jobGroup, int jobId, int status, String filterTime) {

        List<XxlJobLog> xxlJobLogs = jobTopologyApiService.getJobTopologyList(start, length, jobGroup, jobId, status, filterTime);
        //统计任务链的成功率
        Map<String, Object> resultMap = new HashMap<>();

        long failCount = xxlJobLogs.stream().filter(item -> item.getTaskChainStatus() == 2).count();
        resultMap.put("count", xxlJobLogs.size());
        resultMap.put("failCount", failCount);
        resultMap.put("data", xxlJobLogs);
        return new ReturnT(resultMap);
    }

    private int count(JobTopology topology) {
        if (topology == null) {
            return 2;
        }
        List<JobTopology> childJobTopologys = topology.getChildJobTopology();
        if (childJobTopologys != null && childJobTopologys.size() > 0) {
            for (JobTopology childJobTopology : childJobTopologys) {
                return count(childJobTopology);
            }
        } else {
            if (topology.getJobStatus() == 1) {
                return 1;   //成功
            } else if (topology.getJobStatus() == 2 || topology.getJobStatus() == 3) {
                return 2;   //失败
            } else if (topology.getJobStatus() == 4) {
                return 3;   //执行中
            } else {
                return 0;
            }
        }
        return 0;
    }


    /**
     * 任务链拓扑图
     *
     * @param masterJobId
     * @return
     */
    @GetMapping("/job")
    public ReturnT jobTopology(int masterJobId) {
        JobTopology jobChain = jobTopologyApiService.getTopologyJobChain(masterJobId);
        return new ReturnT(jobChain);
    }


    @PostMapping("/add")
    public ReturnT addAfterPreJob(int preJobId, int childJobId) {
        return jobTopologyApiService.addAfterPreJob(preJobId, childJobId);
    }

    @GetMapping("/disconnect")
    public ReturnT disconnect(int preJobId, int behindJobId) {
        return jobTopologyApiService.disconnect(preJobId, behindJobId);
    }

    @DeleteMapping("/deleteJobChain")
    public ReturnT deleteJobChain(int jobId) {
        return jobTopologyApiService.deleteJobChain(jobId);
    }


}
