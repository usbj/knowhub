package com.knowhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * knowhub 模块异步配置。rookie 框架已有 {@code AsyncConfig}（@EnableAsync + logExecutor），
 * 但 logExecutor 队列小（2000）+ DiscardOldestPolicy（日志可丢），不适合长耗时迁移任务。
 * <p>
 * 这里为 OSS 数据迁移单独建 migrationExecutor 线程池：
 * - corePoolSize=2：迁移是低频运维操作，并发量极低
 * - maxPoolSize=4：同时最多 4 个迁移任务
 * - queueCapacity=10：迁移任务重，不允许堆积，超限直接拒绝并提示管理员稍后重试
 * - rejectedExecutionHandler=AbortPolicy：拒绝时抛 RejectedExecutionException，由调用方提示
 * - waitForTasksToCompleteOnShutdown=true + awaitTerminationSeconds=600：迁移可能跑很久，关闭时给足时间完成
 * <p>
 * @Async("migrationExecutor") 显式指定，避免占日志池。@EnableAsync 已由 rookie AsyncConfig 启用，此处不重复。
 */
@Configuration
public class MigrationAsyncConfig {

    @Bean("migrationExecutor")
    public ThreadPoolTaskExecutor migrationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("file-migration-");
        // 迁移任务重、不可丢，超限直接拒绝并提示管理员
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(600);
        executor.initialize();
        return executor;
    }
}
