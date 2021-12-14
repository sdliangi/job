package com.xxl.job.admin.controller.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * web mvc config
 *
 * @author xuxueli 2018-04-02 20:48:20
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {


    @Resource
    private UserInterceptor userInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        List<String> excludePath = new ArrayList<>();
        excludePath.add("/api/registry");
        excludePath.add("/toLogin");
        excludePath.add("/login");
        excludePath.add("/comm/handlers");
        excludePath.add("/comm/jobEnum");
        registry.addInterceptor(userInterceptor).addPathPatterns("/**").excludePathPatterns(excludePath);
    }

}