package com.rookie.framework.handle;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.rookie.common.enums.ErrorSourceType;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import com.rookie.common.util.ServletUtil;
import com.rookie.common.pojo.entity.SysErrorLog;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.framework.service.ErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Date;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 切面在失败路径同步写操作日志后，把 oper_id 塞进此 request attribute，供此处关联错误日志 */
    public static final String OPER_LOG_ID_ATTR = "operLogId";

    @Autowired
    private ErrorLogService errorLogService;

    @ExceptionHandler(ServiceException.class)
    public Result serviceExceptionHandle(ServiceException e) {
        if (e.getErrorMsg() != null && !e.getErrorMsg().isEmpty()) {
            log.error("业务异常详情 >>> {}", e.getErrorMsg());
        }
        log.error("系统发生了一个业务错误，请求路径:{} >>> {}", ServletUtil.getRequestCompleteURL(), e.getMessage());
        recordErrorLog(e, ServletUtil.getRequestCompleteURL());
        return Result.error(e.getCode() != null ? e.getCode() : 500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result exceptionHandle(Exception e) {
        log.error("系统发生了一个未知错误，请求路径:{} >>> {}", ServletUtil.getRequestCompleteURL(), e.getMessage());
        printExceptionLocation(e);
        recordErrorLog(e, ServletUtil.getRequestCompleteURL());
        return Result.error();
    }

    private void printExceptionLocation(Exception e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        StackTraceElement firstFrame = stackTrace[0];
        log.error("异常的位置 >>> {}:{}", firstFrame.getFileName(), firstFrame.getLineNumber());
    }

    /**
     * 记录错误日志（REQUEST 来源）
     * - title：请求路径简述
     * - oper_name：从 SecurityContext 取，无登录态置空
     * - oper_log_id：从 request attribute 取（仅接口带 @Log 且失败时由切面塞入），无则空
     * - 异常三件套：类型/消息/完整堆栈
     * 异步落库，不阻塞响应
     */
    private void recordErrorLog(Throwable e, String title) {
        try {
            SysErrorLog errorLog = new SysErrorLog();
            errorLog.setSourceType(ErrorSourceType.REQUEST.getCode());
            errorLog.setTitle(title);
            errorLog.setExceptionType(e.getClass().getName());
            errorLog.setExceptionMsg(ExceptionUtil.getMessage(e));
            errorLog.setExceptionStack(ExceptionUtil.stacktraceToString(e));
            errorLog.setErrorTime(new Date());

            // 操作人：有登录态才填，匿名/未认证置空
            String operName = currentOperName();
            if (operName != null && !operName.isEmpty()) {
                errorLog.setOperName(operName);
            }

            // 关联操作日志主键：切面失败路径塞入的 request attribute
            HttpServletRequest request = ServletUtil.getRequest();
            Object operLogId = request.getAttribute(OPER_LOG_ID_ATTR);
            if (operLogId instanceof Long id) {
                errorLog.setOperLogId(id);
            }

            errorLogService.saveAsync(errorLog);
        } catch (Exception ignore) {
            // 日志采集本身绝不能再抛异常，否则会干扰正常响应
        }
    }

    /**
     * 从 SecurityContext 取当前操作人用户名，无登录态或异常时返回 null
     */
    private String currentOperName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof UserInfo userInfo) {
                return userInfo.getUsername();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
