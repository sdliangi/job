package com.xxl.job.admin.controller.api.pro;

import com.xxl.job.admin.controller.interceptor.UserInterceptor;
import com.xxl.job.admin.core.model.GlueCodeVo;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLogGlue;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobLogGlueDao;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.glue.GlueTypeEnum;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/pro/code")
public class JobCodeApiController {

    @Resource
    private XxlJobInfoDao xxlJobInfoDao;
    @Resource
    private XxlJobLogGlueDao xxlJobLogGlueDao;

    @GetMapping()
    public ReturnT index(int jobId) {
        XxlJobInfo jobInfo = xxlJobInfoDao.loadById(jobId);
        List<XxlJobLogGlue> jobLogGlues = xxlJobLogGlueDao.findByJobId(jobId);

        if (jobInfo == null) {
            throw new RuntimeException(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }
        if (jobInfo.getProjectId() != Integer.parseInt(UserInterceptor.projectThread.get())) {
            return ReturnT.FAIL;
        }
        if (GlueTypeEnum.BEAN == GlueTypeEnum.match(jobInfo.getGlueType())) {
            throw new RuntimeException(I18nUtil.getString("jobinfo_glue_gluetype_unvalid"));
        }
        Map<String, Object> resultParam = new HashMap<>();
        // Glue类型-字典
        resultParam.put("GlueTypeEnum", GlueTypeEnum.values());
        resultParam.put("jobInfo", jobInfo);
        resultParam.put("jobLogGlues", jobLogGlues);
        return new ReturnT(resultParam);
    }

    @PostMapping("/save")
    public ReturnT<String> save(@RequestBody GlueCodeVo glueCodeVo) {
        // valid
        if (glueCodeVo.getGlueRemark() == null) {
            return new ReturnT<String>(500, (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobinfo_glue_remark")));
        }
        if (glueCodeVo.getGlueRemark().length() < 4 || glueCodeVo.getGlueRemark().length() > 100) {
            return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_remark_limit"));
        }
        XxlJobInfo exists_jobInfo = xxlJobInfoDao.loadById(glueCodeVo.getId());
        if (exists_jobInfo == null) {
            return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
        }
        if (exists_jobInfo.getProjectId() != Integer.parseInt(UserInterceptor.projectThread.get())) {
            return ReturnT.FAIL;
        }

        // update new code
        exists_jobInfo.setGlueSource(glueCodeVo.getGlueSource());
        exists_jobInfo.setGlueRemark(glueCodeVo.getGlueRemark());
        exists_jobInfo.setGlueUpdatetime(new Date());

        exists_jobInfo.setUpdateTime(new Date());
        xxlJobInfoDao.update(exists_jobInfo);

        // log old code
        XxlJobLogGlue xxlJobLogGlue = new XxlJobLogGlue();
        xxlJobLogGlue.setJobId(exists_jobInfo.getId());
        xxlJobLogGlue.setGlueType(exists_jobInfo.getGlueType());
        xxlJobLogGlue.setGlueSource(glueCodeVo.getGlueSource());
        xxlJobLogGlue.setGlueRemark(glueCodeVo.getGlueRemark());

        xxlJobLogGlue.setAddTime(new Date());
        xxlJobLogGlue.setUpdateTime(new Date());
        xxlJobLogGlueDao.save(xxlJobLogGlue);

        // remove code backup more than 30
        xxlJobLogGlueDao.removeOld(exists_jobInfo.getId(), 30);

        return ReturnT.SUCCESS;
    }

}
