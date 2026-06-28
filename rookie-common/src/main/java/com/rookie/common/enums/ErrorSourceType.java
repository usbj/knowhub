package com.rookie.common.enums;

/**
 * 错误日志来源类型
 * 对应字典 sys_error_source_type，code 值与字典 dict_data_value 一致
 */
public enum ErrorSourceType {

    REQUEST("REQUEST", "请求触发"),
    SCHEDULED("SCHEDULED", "定时任务"),
    ASYNC("ASYNC", "异步任务"),
    EVENT("EVENT", "事件监听"),
    INIT("INIT", "启动初始化"),
    OTHER("OTHER", "其他");

    private final String code;

    private final String desc;

    ErrorSourceType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
