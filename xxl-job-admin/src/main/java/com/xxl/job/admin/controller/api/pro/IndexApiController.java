package com.xxl.job.admin.controller.api.pro;

import com.xxl.job.admin.controller.interceptor.UserInterceptor;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;


@RestController
@RequestMapping("/pro/index")
public class IndexApiController {

    @Resource
    private XxlJobService xxlJobService;

    @GetMapping()
    public ReturnT index() {
        Map<String, Object> map = xxlJobService.dashboardInfo(Integer.parseInt(UserInterceptor.projectThread.get()));
        return new ReturnT(map);
    }

    @GetMapping("/chartInfo")
    public ReturnT<Map<String, Object>> chartInfo(Date startDate, Date endDate) {
        ReturnT<Map<String, Object>> chartInfo = xxlJobService.chartInfo(startDate, endDate, Integer.parseInt(UserInterceptor.projectThread.get()));
        return chartInfo;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setLenient(false);
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
    }

}
