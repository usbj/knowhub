package com.rookie.framework.config;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
//设置要扫描mapper的位置
@MapperScan("com.rookie.**.mapper")
public class ApplicationConfig {
}
