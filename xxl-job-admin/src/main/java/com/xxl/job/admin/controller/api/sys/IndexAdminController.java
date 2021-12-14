package com.xxl.job.admin.controller.api.sys;

import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;


@RestController
@RequestMapping("/sys/index")
public class IndexAdminController {

    @Resource
    private XxlJobService xxlJobService;

    @GetMapping
    public ReturnT index(@RequestParam(required = false, defaultValue = "0") int projectId) {
        Map<String, Object> map = xxlJobService.dashboardInfo(projectId);
        //sss
        return new ReturnT(map);
    }

    @GetMapping("/chartInfo")
    public ReturnT<Map<String, Object>> chartInfo(Date startDate, Date endDate, @RequestParam(required = false, defaultValue = "0") int projectId) {
        ReturnT<Map<String, Object>> chartInfo = xxlJobService.chartInfo(startDate, endDate, projectId);
        return chartInfo;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setLenient(false);
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
    }

}
