package com.xxl.job.admin.core.util;

public enum OnceJobUtils {
    KAFKA(1, "kafka:5420f45c15fb4746abe3d5114a273662", "sendToKafka");


    private int JOB_GROUP;
    private String JOB_DESC;
    private String JOB_HANDLER;

    OnceJobUtils(int JOB_GROUP, String JOB_DESC, String JOB_HANDLER) {
        this.JOB_GROUP = JOB_GROUP;
        this.JOB_DESC = JOB_DESC;
        this.JOB_HANDLER = JOB_HANDLER;
    }

    public int getJOB_GROUP() {
        return JOB_GROUP;
    }

    public void setJOB_GROUP(int JOB_GROUP) {
        this.JOB_GROUP = JOB_GROUP;
    }

    public String getJOB_DESC() {
        return JOB_DESC;
    }

    public void setJOB_DESC(String JOB_DESC) {
        this.JOB_DESC = JOB_DESC;
    }

    public String getJOB_HANDLER() {
        return JOB_HANDLER;
    }

    public void setJOB_HANDLER(String JOB_HANDLER) {
        this.JOB_HANDLER = JOB_HANDLER;
    }
}
