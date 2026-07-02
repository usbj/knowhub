package com.knowhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 @Scheduled 定时任务支持。
 * 上游框架未声明 @EnableScheduling，本模块自带配置类开启（仅 knowhub 模块内新增文件，
 * 不修改任何 rookie 框架代码/配置，遵守 doc/README.dev.md「rookie 框架代码修改禁令」）。
 * FileGcTask 的 @Scheduled 依赖此配置生效。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
