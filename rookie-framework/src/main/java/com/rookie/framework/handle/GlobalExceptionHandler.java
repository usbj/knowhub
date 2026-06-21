package com.rookie.framework.handle;

import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.rookie.common.util.ServletUtil;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public Result serviceExceptionHandle(ServiceException e){
        if (e.getErrorMsg() != null && !e.getErrorMsg().isEmpty()) {
            log.error("业务异常详情 >>> {}", e.getErrorMsg());
        }
        log.error("系统发生了一个业务错误，请求路径:{} >>> {}", ServletUtil.getRequestCompleteURL(), e.getMessage());
        return Result.error(e.getCode() != null ? e.getCode() : 500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result exceptionHandle(Exception e){
        log.error("系统发生了一个未知错误，请求路径:{} >>> {}", ServletUtil.getRequestCompleteURL(), e.getMessage());
        printExceptionLocation(e);
        return Result.error();
    }

    private void printExceptionLocation(Exception e){
        StackTraceElement[] stackTrace = e.getStackTrace();
        StackTraceElement firstFrame = stackTrace[0];
        log.error("异常的位置 >>> {}:{}", firstFrame.getFileName(), firstFrame.getLineNumber());
    }
}
