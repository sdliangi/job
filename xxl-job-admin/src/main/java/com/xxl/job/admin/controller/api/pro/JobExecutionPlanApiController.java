package com.xxl.job.admin.controller.api.pro;

import com.xxl.job.admin.controller.interceptor.UserInterceptor;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.thread.JobScheduleHelper;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/pro/plan")
public class JobExecutionPlanApiController {
    @Autowired
    private XxlJobInfoDao xxlJobInfoDao;

    @GetMapping("/query")
    public ReturnT jobExecutionPlan(String deadline) {
        Date ofDate = DateUtil.parseDateTime(deadline);
        Map<String, List<String>> maps = new HashMap<>();
        List<XxlJobInfo> xxlJobInfos = xxlJobInfoDao.queryRunningJobByScheduleType(Integer.parseInt(UserInterceptor.projectThread.get()));

        for (XxlJobInfo jobInfo : xxlJobInfos) {
            try {
                Date lastTime = new Date();
                List<String> list = new ArrayList<>();
                while (true) {
                    lastTime = JobScheduleHelper.generateNextValidTime(jobInfo, lastTime);
                    if (lastTime.after(ofDate)) {
                        break;
                    }
                    list.add(DateUtil.formatDateTime(lastTime));
                }
                maps.put(jobInfo.getJobDesc(), list);
            } catch (Exception e) {
                return ReturnT.FAIL;
            }

        }
        return new ReturnT(maps);
    }
}
