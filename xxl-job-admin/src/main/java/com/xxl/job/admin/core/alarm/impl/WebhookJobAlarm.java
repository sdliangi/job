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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Date;

/**
 * Webhook报警
 */
@Component
public class WebhookJobAlarm implements JobAlarm {
    private static Logger logger = LoggerFactory.getLogger(WebhookJobAlarm.class);
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public boolean doAlarm(XxlJobInfo info, XxlJobLog jobLog) {

        if (info != null && !StringUtils.isEmpty(info.getAlarmWebhook())) {
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

            String apiUrl = info.getAlarmWebhook();
            HttpHeaders headers = new HttpHeaders();
            MediaType type = MediaType.parseMediaType("application/json; charset=UTF-8");
            headers.setContentType(type);
            headers.add("Accept", MediaType.APPLICATION_JSON.toString());
            HttpEntity<String> formEntity = new HttpEntity<>(alarmJson.toString(), headers);
            try {
                restTemplate.postForObject(apiUrl, formEntity, String.class);
                return true;
            } catch (Exception e) {
                logger.error(">>>>>>>>>>> job fail alarm webhook-api request error, JobLogId:{}", jobLog.getId(), e);
                return false;
            }
        }

        return true;
    }
}
