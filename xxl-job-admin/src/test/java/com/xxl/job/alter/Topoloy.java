package com.xxl.job.alter;

import com.alibaba.fastjson.JSONObject;
import com.xxl.job.admin.XxlJobAdminApplication;
import com.xxl.job.admin.core.model.JobTopology;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.util.StringUtils;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = XxlJobAdminApplication.class)
public class Topoloy {
    @Autowired
    XxlJobInfoDao xxlJobInfoDao;

    @Test
    public void r() {
        Map<Integer, XxlJobLog> batchLogMaps = new HashMap<>();
        XxlJobLog xxlJobLog1 = new XxlJobLog();
        xxlJobLog1.setJobId(1);
        xxlJobLog1.setJobGroup(2);
        xxlJobLog1.setId(101);
        xxlJobLog1.setTriggerCode(200);
        xxlJobLog1.setHandleCode(200);
        batchLogMaps.put(1, xxlJobLog1);

        XxlJobLog xxlJobLog2 = new XxlJobLog();
        xxlJobLog2.setJobId(2);
        xxlJobLog2.setJobGroup(2);
        xxlJobLog2.setId(102);
        xxlJobLog2.setTriggerCode(200);
        xxlJobLog2.setHandleCode(200);
        batchLogMaps.put(2, xxlJobLog2);

        XxlJobLog xxlJobLog3 = new XxlJobLog();
        xxlJobLog3.setJobId(3);
        xxlJobLog3.setJobGroup(2);
        xxlJobLog3.setId(103);
        xxlJobLog3.setTriggerCode(200);
        xxlJobLog3.setHandleCode(200);
        batchLogMaps.put(3, xxlJobLog3);

        JobTopology jobTopology = getJobTopology(1, batchLogMaps);
        String s = JSONObject.toJSONString(jobTopology);
        System.out.println("s = " + s);


    }

    private JobTopology getJobTopology(int jobId, Map<Integer, XxlJobLog> batchLogMaps) {
        String childJobIds = xxlJobInfoDao.getChildJobIdById(jobId);
        JobTopology jobTopology = new JobTopology();
        XxlJobLog xxlJobLog = batchLogMaps.get(jobId);
        jobTopology.setJobId(xxlJobLog.getJobId());
        jobTopology.setJobGroup(xxlJobLog.getJobGroup());
        jobTopology.setLogId(xxlJobLog.getId());
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
