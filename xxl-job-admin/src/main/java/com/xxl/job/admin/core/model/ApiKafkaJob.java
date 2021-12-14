package com.xxl.job.admin.core.model;

public class ApiKafkaJob {

    private String topic;                //主题

    private String message;                // 消息内容
    private long time;            // 多长时间后执行,单位分钟

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
