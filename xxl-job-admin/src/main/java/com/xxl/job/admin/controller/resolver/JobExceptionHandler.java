package com.xxl.job.admin.controller.resolver;

import com.xxl.job.core.biz.model.ReturnT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class JobExceptionHandler {
    private static transient Logger logger = LoggerFactory.getLogger(JobExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ReturnT handleException(Exception e) {
        logger.error(e.getMessage(), e);
        return new ReturnT(500, "未知异常");
    }
}
