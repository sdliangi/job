package com.xxl.job.executor.service.jobhandler;

import com.alibaba.fastjson.JSONObject;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class JobHandler {
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @XxlJob(value = "sendToKafka")
    public void sendToKafka() {
        String param = XxlJobHelper.getJobParam();
        JSONObject jsonObject = JSONObject.parseObject(param);
        kafkaTemplate.send(jsonObject.getString("topic"), jsonObject.getString("message")).addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
            @Override
            public void onFailure(Throwable throwable) {
                XxlJobHelper.log("发送消息失败:{}", throwable.getMessage());
                XxlJobHelper.handleFail();
            }

            @Override
            public void onSuccess(SendResult<String, Object> stringObjectSendResult) {
                XxlJobHelper.log("发送消息成功");
            }
        });
    }

    /**
     * 删除指定过期时间的一次性任务，Kafka等
     */
    @XxlJob("deleteOnceJobHandler")
    public void deleteKafkaJobHandler() {
        String jobType = XxlJobHelper.getJobParam();
        if (jobType == null || jobType.equals("")) {
            XxlJobHelper.handleFail("jobType不能为空");
            return;
        }
        Map<String, String> map = new HashMap<>();
        map.put("jobType", jobType);
        try {
            ResponseEntity<String> response = restTemplate.exchange(adminAddresses + "/job/deleteOverdueJob?jobType={jobType}",
                    HttpMethod.DELETE, null, String.class, map);
            String body = response.getBody();
            if (body != null) {
                JSONObject object = JSONObject.parseObject(body);
                if (object.getIntValue("code") == 200) {
                    XxlJobHelper.handleSuccess();
                } else {
                    XxlJobHelper.handleFail("任务执行失败");
                }
            }
        } catch (Exception e) {
            XxlJobHelper.handleFail(e.getMessage());
        }

    }

    /**
     * 请求格式为json
     */
    @XxlJob("httpJobHandler")
    public void httpJob() {
        XxlJobHelper.getJobParam();
        String param = XxlJobHelper.getJobParam();
        JSONObject object = JSONObject.parseObject(param);
        String url = object.getString("url");
        String method = object.getString("method");
        Map requestParam = object.getObject("requestParam", Map.class);

        if (url == null || url.trim().length() == 0 && method == null || url.trim().length() == 0) {
            XxlJobHelper.handleFail("数据格式不正确,url 和method不能为空");
            return;
        }
        HttpMethod httpMethod;
        if (method.equalsIgnoreCase("get")) {
            httpMethod = HttpMethod.GET;
        } else if (method.equalsIgnoreCase("post")) {
            httpMethod = HttpMethod.POST;
        } else if (method.equalsIgnoreCase("put")) {
            httpMethod = HttpMethod.PUT;
        } else if (method.equalsIgnoreCase("delete")) {
            httpMethod = HttpMethod.DELETE;
        } else {
            XxlJobHelper.handleFail("Method请求方式不支持");
            return;
        }
        ResponseEntity responseEntity = restTemplate.exchange(url, httpMethod, null, String.class, requestParam);
        XxlJobHelper.log(responseEntity.getBody().toString());
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            XxlJobHelper.handleSuccess(responseEntity.getBody().toString());
        }
    }

}
