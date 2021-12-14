package com.xxl.job.admin.core.complete;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.thread.JobTriggerPoolHelper;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.core.util.StringUtils;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

/**
 * @author xuxueli 2020-10-30 20:43:10
 */
public class XxlJobCompleter {
    private static Logger logger = LoggerFactory.getLogger(XxlJobCompleter.class);

    /**
     * common fresh handle entrance (limit only once)
     *
     * @param xxlJobLog
     * @return
     */
    public static int updateHandleInfoAndFinish(XxlJobLog xxlJobLog) {

        // finish
        finishJob(xxlJobLog);

        // text最大64kb 避免长度过长
        if (xxlJobLog.getHandleMsg().length() > 15000) {
            xxlJobLog.setHandleMsg(xxlJobLog.getHandleMsg().substring(0, 15000));
        }

        // fresh handle
        return XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateHandleInfo(xxlJobLog);
    }


    /**
     * do somethind to finish job
     */
    private static void finishJob(XxlJobLog xxlJobLog) {

        // 1、handle success, to trigger child job
        String triggerChildMsg = null;
        XxlJobInfo xxlJobInfo = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().loadById(xxlJobLog.getJobId());
        if (XxlJobContext.HANDLE_COCE_SUCCESS == xxlJobLog.getHandleCode()) {
            if (xxlJobInfo != null && xxlJobInfo.getChildJobId() != null && xxlJobInfo.getChildJobId().trim().length() > 0) {
                triggerChildMsg = "<br><br><span style=\"color:#00c0ef;\" > >>>>>>>>>>>" + I18nUtil.getString("jobconf_trigger_child_run") + "<<<<<<<<<<< </span><br>";

                String[] childJobIds = xxlJobInfo.getChildJobId().split(",");
                long l = System.currentTimeMillis();
                for (int i = 0; i < childJobIds.length; i++) {
                    int childJobId = (childJobIds[i] != null && childJobIds[i].trim().length() > 0 && isNumeric(childJobIds[i])) ? Integer.valueOf(childJobIds[i]) : -1;
                    if (childJobId > 0) {
                        //taskBatchId，有子任务，设置父任务log批次,并设置子任务log批次
                        String taskBatchId = xxlJobLog.getTaskBatchId();
                        if (taskBatchId == null || taskBatchId.trim().length() == 0) {
                            taskBatchId = xxlJobLog.getJobId() + ":" + l;
                            xxlJobLog.setTaskBatchId(taskBatchId);
                        }
                        XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateTaskBatchInfo(xxlJobLog);
                        JobTriggerPoolHelper.trigger(childJobId, taskBatchId, TriggerTypeEnum.PARENT, -1, null, null, null);
                        ReturnT<String> triggerChildResult = ReturnT.SUCCESS;

                        // add msg
                        triggerChildMsg += MessageFormat.format(I18nUtil.getString("jobconf_callback_child_msg1"),
                                (i + 1),
                                childJobIds.length,
                                childJobIds[i],
                                (triggerChildResult.getCode() == ReturnT.SUCCESS_CODE ? I18nUtil.getString("system_success") : I18nUtil.getString("system_fail")),
                                triggerChildResult.getMsg());
                    } else {
                        triggerChildMsg += MessageFormat.format(I18nUtil.getString("jobconf_callback_child_msg2"),
                                (i + 1),
                                childJobIds.length,
                                childJobIds[i]);
                    }
                }

            }
        } else {
            //任务链中，有一个任务执行失败,就在主任务日志中设置失败状态
            if (xxlJobInfo.getIsMasterJob() == 1) {
                xxlJobLog.setTaskChainStatus(2);
                XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateTaskChainStatus(xxlJobLog);
            }
            if (!StringUtils.isEmpty(xxlJobLog.getTaskBatchId())) {
                String taskBatchId = xxlJobLog.getTaskBatchId();
                String masterJobId = taskBatchId.split(":")[0];
                XxlJobLog masterLog = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().loadMasterLogByJobIdAndBatchId(masterJobId, taskBatchId);
                masterLog.setTaskChainStatus(2);
                XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateTaskChainStatus(masterLog);
            }
        }

        if (triggerChildMsg != null) {
            xxlJobLog.setHandleMsg(xxlJobLog.getHandleMsg() + triggerChildMsg);
        }

        // 2、fix_delay trigger next
        // on the way

    }

    private static boolean isNumeric(String str) {
        try {
            int result = Integer.valueOf(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
