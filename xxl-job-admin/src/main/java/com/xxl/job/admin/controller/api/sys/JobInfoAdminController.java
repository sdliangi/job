package com.xxl.job.admin.controller.api.sys;


import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.thread.JobTriggerPoolHelper;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.core.biz.model.ReturnT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/job")
public class JobInfoAdminController {
    private static Logger logger = LoggerFactory.getLogger(JobInfoAdminController.class);
    @Resource
    public XxlJobInfoDao xxlJobInfoDao;
    @Resource
    private XxlJobService xxlJobService;


    @GetMapping("/pageList")

    public ReturnT pageList(@RequestParam(required = false, defaultValue = "0") int start,
                            @RequestParam(required = false, defaultValue = "10") int length,
                            int jobGroup, int triggerStatus, String jobDesc, String executorHandler, String author, @RequestParam(required = false, defaultValue = "0") int projectId) {
        Map<String, Object> pageList = xxlJobService.pageList(start, length, jobGroup, triggerStatus, jobDesc, executorHandler, author, projectId);
        pageList.remove("recordsFiltered");
        return new ReturnT(pageList);
    }

    @PostMapping("/add")

    public ReturnT<String> add(@RequestBody XxlJobInfo jobInfo) {
        return xxlJobService.add(jobInfo);
    }

    @PutMapping("/update")

    public ReturnT<String> update(@RequestBody XxlJobInfo jobInfo) {

        return xxlJobService.update(jobInfo);
    }

    @DeleteMapping("/remove/{id}")

    public ReturnT<String> remove(@PathVariable int id) {
        return xxlJobService.remove(id, 0);
    }


    @GetMapping("/stop")

    public ReturnT<String> pause(int id) {
        return xxlJobService.stop(id);
    }

    @GetMapping("/start")

    public ReturnT<String> start(int id) {
        return xxlJobService.start(id);
    }

    @GetMapping("/trigger")

    public ReturnT<String> triggerJob(int id, String executorParam, String addressList) {
        // force cover job param
        if (executorParam == null) {
            executorParam = "";
        }

        JobTriggerPoolHelper.trigger(id, null, TriggerTypeEnum.MANUAL, -1, null, executorParam, addressList);
        return ReturnT.SUCCESS;
    }


    @GetMapping("/getJobsByGroup")

    public ReturnT<List<XxlJobInfo>> getJobsByGroup(int jobGroup, @RequestParam(required = false, defaultValue = "0") int projectId) {
        List<XxlJobInfo> list = xxlJobInfoDao.getJobsByGroupAndPid(jobGroup, projectId);
        return new ReturnT(list);
    }

}
