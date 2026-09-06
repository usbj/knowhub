package com.knowhub.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * knowhub 业务模块 Mapper 扫描自注册。
 * 上游 ApplicationConfig 的 @MapperScan("com.rookie.**.mapper") 只覆盖 com.rookie，
 * knowhub 业务在 com.knowhub.mapper，故在此独立声明 @MapperScan，与框架的扫描叠加生效，
 * 不修改任何 rookie 框架代码/配置（遵守 doc/README.dev.md「rookie 框架代码修改禁令」）。
 *
 * 模块由 knowhub-blog 改名整合为 knowhub 后，博客/文件存储等所有 knowhub 业务的 Mapper
 * 统一落在 com.knowhub.mapper，本配置一处扫描全覆盖，后续新增业务子包无需再改此处。
 */
@Configuration
@MapperScan("com.knowhub.mapper")
public class KnowhubMapperConfig {
}
