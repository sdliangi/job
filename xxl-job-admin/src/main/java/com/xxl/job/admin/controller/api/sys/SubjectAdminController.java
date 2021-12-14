package com.xxl.job.admin.controller.api.sys;


import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/sys/subject")
public class SubjectAdminController {
    @Autowired
    private RestTemplate restTemplate;


    @GetMapping("/list")
    public ReturnT getAllProject() {
        Map map = restTemplate.getForObject(XxlJobAdminConfig.getAdminConfig().getAuthServer() + "auth-v3/project/all/noPage", Map.class);
        if (map.get("projects") == null) {
            return new ReturnT(500, "无法获取项目列表");
        }
        return new ReturnT(map.get("projects"));
    }
}
