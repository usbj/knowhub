package com.rookie.framework.aspectj;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import cn.hutool.json.JSONUtil;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.enums.DeviceType;
import com.rookie.common.pojo.entity.SysOperLog;
import com.rookie.common.util.ServletUtil;
import com.rookie.framework.handle.GlobalExceptionHandler;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.framework.service.OperLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.Map;

/**
 * 操作日志切面
 * 采集策略：请求线程内同步采集全部字段（SecurityContext/RequestContext 是线程本地，@Async 新线程拿不到）
 * 落库策略：
 *   - 成功：异步 saveAsync，不阻塞业务
 *   - 失败：同步 saveAndGetId 拿 oper_id 塞进 request attribute，供 GlobalExceptionHandler 写错误日志时关联 oper_log_id
 * 异常继续向上抛出，交由 GlobalExceptionHandler 统一处理响应与错误日志
 */
@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    /** 操作参数/返回结果最大保留长度，避免超长内容撑爆日志表 */
    private static final int MAX_DATA_LENGTH = 2000;

    @Autowired
    private OperLogService operLogService;

    @Pointcut("@annotation(com.rookie.common.annotation.Log)")
    public void logPointcut() {
    }

    @Around("logPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 请求线程内同步采集上下文
        SysOperLog operLog = buildOperLog(joinPoint);
        long start = System.currentTimeMillis();
        Throwable thrown = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            // 2. 计算耗时
            operLog.setCostTime(System.currentTimeMillis() - start);

            try {
                if (thrown != null) {
                    // 失败路径：同步落库拿主键，供错误日志关联，并记录失败状态
                    operLog.setStatus(1);
                    operLog.setJsonResult(null);
                    Long operId = operLogService.saveAndGetId(operLog);
                    // 塞进 request attribute，GlobalExceptionHandler 写错误日志时取用
                    setOperLogIdAttribute(operId);
                } else {
                    // 成功路径：异步落库
                    operLog.setStatus(0);
                    Log ann = getLogAnnotation(joinPoint);
                    if (ann != null && ann.isSaveResponseData() && result != null) {
                        operLog.setJsonResult(substring(JSONUtil.toJsonStr(result)));
                    }
                    operLogService.saveAsync(operLog);
                }
            } catch (Exception ex) {
                // 日志落库本身绝不能干扰业务，吞掉并告警
                log.warn("操作日志落库失败，title={} >>> {}", operLog.getTitle(), ex.getMessage());
            }
        }
    }

    /**
     * 在请求线程内同步采集操作日志全部字段
     */
    private SysOperLog buildOperLog(ProceedingJoinPoint joinPoint) {
        SysOperLog operLog = new SysOperLog();
        operLog.setOperTime(new Date());

        // 注解信息
        Log ann = getLogAnnotation(joinPoint);
        if (ann != null) {
            operLog.setTitle(ann.title());
            operLog.setBusinessType(ann.businessType() != null ? ann.businessType().getCode() : BusinessType.OTHER.getCode());
            operLog.setOperParam(substring(JSONUtil.toJsonStr(joinPoint.getArgs())));
        } else {
            operLog.setBusinessType(BusinessType.OTHER.getCode());
        }

        // 方法名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        operLog.setMethod(joinPoint.getTarget().getClass().getName() + "." + method.getName());

        // 请求上下文
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            operLog.setRequestMethod(request.getMethod());
            operLog.setOperUrl(request.getRequestURI());
            operLog.setOperIp(JakartaServletUtil.getClientIP(request));

            // UA 解析 -> 操作系统/浏览器/设备类型
            String ua = request.getHeader("User-Agent");
            if (StrUtil.isNotBlank(ua)) {
                UserAgent userAgent = UserAgentUtil.parse(ua);
                if (userAgent != null) {
                    operLog.setOperOs(userAgent.getOs() != null ? userAgent.getOs().getName() : "");
                    operLog.setOperBrowser(userAgent.getBrowser() != null ? userAgent.getBrowser().getName() : "");
                    operLog.setDeviceType(resolveDeviceType(userAgent));
                }
            }

            // 请求体：从 ContentCachingRequestWrapper 缓存中读取（仅当 isSaveRequestData 时）
            if (ann != null && ann.isSaveRequestData() && operLog.getOperParam() == null) {
                String body = readRequestBody(request);
                if (StrUtil.isNotBlank(body)) {
                    operLog.setOperParam(substring(body));
                }
            }
        }

        // 操作人：有登录态才填，匿名/未认证置空
        String operName = currentOperName();
        if (operName != null && !operName.isEmpty()) {
            operLog.setOperName(operName);
        } else {
            operLog.setOperName("");
        }

        return operLog;
    }

    private Log getLogAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getAnnotation(Log.class);
    }

    private String resolveDeviceType(UserAgent userAgent) {
        if (userAgent.isMobile()) {
            return DeviceType.MOBILE.getCode();
        }
        // hutool 对平板判断较弱，移动端之外统一按 PC，识别不准时表现为 PC（UNKNOWN 暂不使用，避免噪声）
        return DeviceType.PC.getCode();
    }

    /**
     * 读取请求体：RequestCachingFilter 已用 ContentCachingRequestWrapper 包装，
     * Controller @RequestBody 读取后内容缓存在字节数组中，此处可再次读取
     */
    private String readRequestBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf != null && buf.length > 0) {
                return StrUtil.utf8Str(buf);
            }
        }
        return null;
    }

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

    private void setOperLogIdAttribute(Long operId) {
        if (operId == null) {
            return;
        }
        try {
            ServletUtil.getRequest().setAttribute(GlobalExceptionHandler.OPER_LOG_ID_ATTR, operId);
        } catch (Exception ignore) {
        }
    }

    private String substring(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > MAX_DATA_LENGTH ? s.substring(0, MAX_DATA_LENGTH) : s;
    }
}
