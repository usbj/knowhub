package com.rookie.framework.config;

import com.rookie.common.enums.ErrorSourceType;
import com.rookie.common.pojo.entity.SysErrorLog;
import com.rookie.framework.service.ErrorLogService;
import cn.hutool.core.exceptions.ExceptionUtil;
import jakarta.annotation.Resource;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步配置
 * 1. 提供 logExecutor 线程池：操作日志/错误日志的异步落库共用
 * 2. 拒绝策略 DiscardOldestPolicy：日志是辅助产物，高负载时宁可丢最旧的一条也不阻塞业务请求
 * 3. 实现 AsyncUncaughtExceptionHandler：@Async 方法抛出的未捕获异常统一进错误日志(ASYNC 来源)
 */
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Resource
    private ErrorLogService errorLogService;

    /**
     * 日志异步落库线程池
     */
    @Bean("logExecutor")
    public ThreadPoolTaskExecutor logExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("log-async-");
        // 日志可丢，绝不拖垮主流程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 默认异步执行器指向 logExecutor，使 @Async 不指定时也走日志线程池
     */
    @Override
    public Executor getAsyncExecutor() {
        return logExecutor();
    }

    /**
     * @Async 方法未捕获异常处理器：统一记录到错误日志，来源标记为 ASYNC
     * 注意：异步线程无 SecurityContext/RequestContext，oper_name 与 oper_log_id 留空
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            SysErrorLog errorLog = new SysErrorLog();
            errorLog.setSourceType(ErrorSourceType.ASYNC.getCode());
            errorLog.setTitle(method.getDeclaringClass().getSimpleName() + "." + method.getName());
            errorLog.setExceptionType(ex.getClass().getName());
            errorLog.setExceptionMsg(ExceptionUtil.getMessage(ex));
            errorLog.setExceptionStack(ExceptionUtil.stacktraceToString(ex));
            errorLog.setErrorTime(new Date());
            errorLogService.saveAsync(errorLog);
        };
    }
}
