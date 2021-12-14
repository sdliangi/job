package com.xxl.job.admin.core.alarm.impl;

import com.alibaba.fastjson.JSONObject;
import com.xxl.job.admin.core.alarm.JobAlarm;
import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.util.StringUtils;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.ExecutionException;

/**
 * 任务失败，向MQ发送报警信息
 */
@Component
public class MQJobAlarm implements JobAlarm {
    private static Logger logger = LoggerFactory.getLogger(MQJobAlarm.class);
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public boolean doAlarm(XxlJobInfo info, XxlJobLog jobLog) {
        if (!StringUtils.isEmpty(info.getAlarmMq())) {
            XxlJobGroup group = XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().load(Integer.valueOf(info.getJobGroup()));
            JSONObject alarmJson = new JSONObject();
            alarmJson.put("title", "任务调度平台监控报警");
            alarmJson.put("date", DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
            alarmJson.put("group", group.getTitle());
            alarmJson.put("jobId", info.getId());
            alarmJson.put("jobDesc", info.getJobDesc());
            alarmJson.put("logId", jobLog.getId());
            alarmJson.put("triggerCode", jobLog.getTriggerCode());
            alarmJson.put("triggerMsg", jobLog.getTriggerMsg());
            alarmJson.put("handleCode", jobLog.getHandleCode());
            alarmJson.put("handleMsg", jobLog.getHandleMsg());


            //同步发送方式
            try {
                kafkaTemplate.send(info.getAlarmMq(), alarmJson.toString()).get();
            } catch (InterruptedException e) {
                logger.error(">>>>>>>>>>> xxl-job, job fail alarm MQ send error, JobLogId:{},{}", jobLog.getId(), e.getMessage());
                return false;
            } catch (ExecutionException e) {
                logger.error(">>>>>>>>>>> xxl-job, job fail alarm MQ send error, JobLogId:{},{}", jobLog.getId(), e.getMessage());
                return false;
            }
        }
        return true;
    }
}
