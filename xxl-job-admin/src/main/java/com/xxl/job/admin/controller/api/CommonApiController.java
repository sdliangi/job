package com.xxl.job.admin.controller.api;


import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.route.ExecutorRouteStrategyEnum;
import com.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import com.xxl.job.admin.core.thread.JobScheduleHelper;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.core.util.StringUtils;
import com.xxl.job.admin.dao.XxlJobGroupDao;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.client.ExecutorBizClient;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/comm")
public class CommonApiController {
    private static Logger logger = LoggerFactory.getLogger(CommonApiController.class);
    @Autowired
    private XxlJobGroupDao xxlJobGroupDao;


    @GetMapping("/handlers")
    public ReturnT getHandlers(int groupId) {
        XxlJobGroup jobGroup = xxlJobGroupDao.load(groupId);
        if (jobGroup == null) {
            return new ReturnT(ReturnT.FAIL_CODE, "执行器不存在");
        }
        String addressList = jobGroup.getAddressList();
        if (StringUtils.isEmpty(addressList)) {
            return new ReturnT(ReturnT.FAIL_CODE, "没有在线的执行器");
        }
        //http://10.233.96.168:9090/,http://10.233.96.183:9090/,http://10.233.96.215:9090/,http://10.233.96.22:9090/,http://10.233.96.24:9090/
        String url = addressList.split(",")[0];
        ExecutorBiz executorBiz = new ExecutorBizClient(url, XxlJobAdminConfig.getAdminConfig().getAccessToken());
        ReturnT<Set<String>> handlers = executorBiz.handlers();
        return handlers;
    }

    @GetMapping("/jobEnum")

    public ReturnT<Map> index(@RequestParam(required = false, defaultValue = "-1") int jobGroup) {
        Map<String, Object> resultParam = new HashMap<>();
        // 枚举-字典
        resultParam.put("ExecutorRouteStrategyEnum", ExecutorRouteStrategyEnum.values());        // 路由策略-列表
        resultParam.put("GlueTypeEnum", GlueTypeEnum.values());                                // Glue类型-字典
        resultParam.put("ExecutorBlockStrategyEnum", ExecutorBlockStrategyEnum.values());        // 阻塞处理策略-字典
        resultParam.put("ScheduleTypeEnum", ScheduleTypeEnum.values());                        // 调度类型
        resultParam.put("MisfireStrategyEnum", MisfireStrategyEnum.values());                    // 调度过期策略

        // 执行器列表
        List<XxlJobGroup> jobGroupList_all = xxlJobGroupDao.findAll();

        resultParam.put("JobGroupList", jobGroupList_all);
//        resultParam.put("jobGroup", jobGroup);

        return new ReturnT(resultParam);
    }

    @GetMapping("/nextTriggerTime")

    public ReturnT<List<String>> nextTriggerTime(String scheduleType, String scheduleConf) {

        XxlJobInfo paramXxlJobInfo = new XxlJobInfo();
        paramXxlJobInfo.setScheduleType(scheduleType);
        paramXxlJobInfo.setScheduleConf(scheduleConf);

        List<String> result = new ArrayList<>();
        try {
            Date lastTime = new Date();
            for (int i = 0; i < 5; i++) {
                lastTime = JobScheduleHelper.generateNextValidTime(paramXxlJobInfo, lastTime);
                if (lastTime != null) {
                    result.add(DateUtil.formatDateTime(lastTime));
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new ReturnT<>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type") + I18nUtil.getString("system_unvalid")) + e.getMessage());
        }
        return new ReturnT<>(result);
    }

}
