package com.xxl.job.admin.controller.api.sys;


import com.xxl.job.admin.core.complete.XxlJobCompleter;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.scheduler.XxlJobScheduler;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobLogDao;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.model.KillParam;
import com.xxl.job.core.biz.model.LogParam;
import com.xxl.job.core.biz.model.LogResult;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/log")
public class JobLogAdminController {
    private static Logger logger = LoggerFactory.getLogger(JobLogAdminController.class);
    @Resource
    public XxlJobInfoDao xxlJobInfoDao;
    @Resource
    public XxlJobLogDao xxlJobLogDao;


    @GetMapping("/pageList")

    public ReturnT pageList(@RequestParam(required = false, defaultValue = "0") int start,
                            @RequestParam(required = false, defaultValue = "10") int length,
                            int jobGroup, int jobId, int logStatus, String filterTime,
                            @RequestParam(required = false, defaultValue = "0") int projectId) {
        // parse param
        Date triggerTimeStart = null;
        Date triggerTimeEnd = null;
        if (filterTime != null && filterTime.trim().length() > 0) {
            String[] temp = filterTime.split(" - ");
            if (temp.length == 2) {
                triggerTimeStart = DateUtil.parseDateTime(temp[0]);
                triggerTimeEnd = DateUtil.parseDateTime(temp[1]);
            }
        }
        // package result
        Map<String, Object> maps = new HashMap<String, Object>();
        // page query
        List<XxlJobLog> list = xxlJobLogDao.pageList(start, length, jobGroup, jobId, triggerTimeStart, triggerTimeEnd, logStatus, projectId);
        int list_count = xxlJobLogDao.pageListCount(start, length, jobGroup, jobId, triggerTimeStart, triggerTimeEnd, logStatus, projectId);
        if (logStatus == 0 && jobId > 0) {   //???????????????
            long successCount = list.stream().filter(item -> item.getHandleCode() == 200).count();
            long failCount = list.stream().filter(item -> item.getTriggerCode() != 0 || item.getTriggerCode() != 200 || item.getHandleCode() != 0 || item.getHandleCode() != 200).count();
            long runningCount = list.stream().filter(item -> item.getTriggerCode() == 200 && item.getHandleCode() == 0).count();
            maps.put("successCount", successCount);
            maps.put("failCount", failCount);
            maps.put("runningCount", runningCount);
        }
        maps.put("recordsTotal", list_count);        // ????????????
        maps.put("data", list);                    // ????????????
        return new ReturnT(maps);
    }

    @GetMapping("/logDetailPage")

    public ReturnT logDetailPage(int id) {
        Map<String, Object> map = new HashMap<>();
        // base check
        XxlJobLog jobLog = xxlJobLogDao.load(id);
        if (jobLog == null) {
            throw new RuntimeException(I18nUtil.getString("joblog_logid_unvalid"));
        }

        map.put("triggerCode", jobLog.getTriggerCode());
        map.put("handleCode", jobLog.getHandleCode());
        map.put("executorAddress", jobLog.getExecutorAddress());
        map.put("triggerTime", jobLog.getTriggerTime().getTime());
        map.put("logId", jobLog.getId());
        return new ReturnT(map);
    }

    @GetMapping("/logDetailCat")

    public ReturnT<LogResult> logDetailCat(String executorAddress, long triggerTime, long logId, int fromLineNum) {
        try {
            ExecutorBiz executorBiz = XxlJobScheduler.getExecutorBiz(executorAddress);
            ReturnT<LogResult> logResult = executorBiz.log(new LogParam(triggerTime, logId, fromLineNum));

            // is end
            if (logResult.getData() != null && logResult.getData().getFromLineNum() > logResult.getData().getToLineNum()) {
                XxlJobLog jobLog = xxlJobLogDao.load(logId);
                if (jobLog.getHandleCode() > 0) {
                    logResult.getData().setEnd(true);
                }
            }

            return logResult;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new ReturnT<LogResult>(ReturnT.FAIL_CODE, e.getMessage());
        }
    }

    @GetMapping("/logKill")

    public ReturnT<String> logKill(int id) {
        // base check
        XxlJobLog log = xxlJobLogDao.load(id);
        XxlJobInfo jobInfo = xxlJobInfoDao.loadById(log.getJobId());
        if (jobInfo == null) {
            return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }
        if (ReturnT.SUCCESS_CODE != log.getTriggerCode()) {
            return new ReturnT<String>(500, I18nUtil.getString("joblog_kill_log_limit"));
        }

        // request of kill
        ReturnT<String> runResult = null;
        try {
            ExecutorBiz executorBiz = XxlJobScheduler.getExecutorBiz(log.getExecutorAddress());
            runResult = executorBiz.kill(new KillParam(jobInfo.getId()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            runResult = new ReturnT<String>(500, e.getMessage());
        }

        if (ReturnT.SUCCESS_CODE == runResult.getCode()) {
            log.setHandleCode(ReturnT.FAIL_CODE);
            log.setHandleMsg(I18nUtil.getString("joblog_kill_log_byman") + ":" + (runResult.getMsg() != null ? runResult.getMsg() : ""));
            log.setHandleTime(new Date());
            XxlJobCompleter.updateHandleInfoAndFinish(log);
            return new ReturnT<String>(runResult.getMsg());
        } else {
            return new ReturnT<String>(500, runResult.getMsg());
        }
    }

    @DeleteMapping("/clearLog")

    public ReturnT<String> clearLog(int jobGroup, int jobId, int type) {

        Date clearBeforeTime = null;
        int clearBeforeNum = 0;
        if (type == 1) {
            clearBeforeTime = DateUtil.addMonths(new Date(), -1);    // ?????????????????????????????????
        } else if (type == 2) {
            clearBeforeTime = DateUtil.addMonths(new Date(), -3);    // ?????????????????????????????????
        } else if (type == 3) {
            clearBeforeTime = DateUtil.addMonths(new Date(), -6);    // ?????????????????????????????????
        } else if (type == 4) {
            clearBeforeTime = DateUtil.addYears(new Date(), -1);    // ??????????????????????????????
        } else if (type == 5) {
            clearBeforeNum = 1000;        // ?????????????????????????????????
        } else if (type == 6) {
            clearBeforeNum = 10000;        // ?????????????????????????????????
        } else if (type == 7) {
            clearBeforeNum = 30000;        // ?????????????????????????????????
        } else if (type == 8) {
            clearBeforeNum = 100000;    // ?????????????????????????????????
        } else if (type == 9) {
            clearBeforeNum = 0;            // ????????????????????????
        } else {
            return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("joblog_clean_type_unvalid"));
        }

        List<Long> logIds = null;
        do {
            logIds = xxlJobLogDao.findClearLogIds(jobGroup, jobId, clearBeforeTime, clearBeforeNum, 1000, 0);
            if (logIds != null && logIds.size() > 0) {
                xxlJobLogDao.clearLog(logIds);
            }
        } while (logIds != null && logIds.size() > 0);

        return ReturnT.SUCCESS;
    }

}
