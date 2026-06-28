package com.rookie.common.annotation;

import com.rookie.common.enums.BusinessType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 标注在 Controller 方法上，由 LogAspect 切面采集并落库到 sys_oper_log
 * 与鉴权注解 @PreAuthorize 各管各的：本注解只负责"做了什么、结果如何"，不参与能否访问的判定
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** 模块标题，如"消息通知" */
    String title() default "";

    /** 业务类型，默认 OTHER */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否记录请求参数，默认 true */
    boolean isSaveRequestData() default true;

    /** 是否记录返回结果，默认 true */
    boolean isSaveResponseData() default true;
}
