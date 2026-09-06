package com.knowhub.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * knowhub 业务模块 springdoc 分组自注册。
 * 上游 application.yml 的 springdoc.group-configs 只扫 com.rookie.system.controller，
 * knowhub 业务在 com.knowhub.controller，故在此编程式声明一个 GroupedOpenApi Bean，
 * springdoc 收集所有 GroupedOpenApi Bean 即可让 knowhub 接口进入 swagger-ui，
 * 不修改任何 rookie 框架代码/配置（遵守 doc/README.dev.md「rookie 框架代码修改禁令」）。
 *
 * 模块由 knowhub-blog 改名整合为 knowhub 后，博客/文件存储等所有 knowhub 业务的 Controller
 * 统一落在 com.knowhub.controller，本分组一处扫描全覆盖，后续新增业务子包无需再改此处。
 */
@Configuration
public class BlogOpenApiConfig {

    @Bean
    public GroupedOpenApi blogGroup() {
        return GroupedOpenApi.builder()
                .group("knowhub")
                .pathsToMatch("/**")
                .packagesToScan("com.knowhub.controller")
                .build();
    }
}
