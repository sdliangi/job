package com.xxl.job.admin.controller.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

@Component
public class UserInterceptor implements HandlerInterceptor {
    public static ThreadLocal<String> projectThread = new ThreadLocal<>();


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String projectId = request.getHeader("projectId");
        String uri = request.getRequestURI();

        if (uri.startsWith(request.getContextPath() + "/pro") && projectId == null) {
            response.setHeader("Content-Type", "application/json;charset=UTF-8");
            PrintWriter writer = response.getWriter();
            writer.println(JSONObject.toJSONString(new ReturnT<>(500, "项目id缺失")));
            writer.flush();
            writer.close();
            return false;
        }
        if (projectId == null) {
            projectId = "0";
        }
        projectThread.set(projectId);
        return true;
    }
}
