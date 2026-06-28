package com.rookie.common.enums;

/**
 * 操作日志业务类型
 * 对应字典 sys_oper_business_type，code 值与字典 dict_data_value 一致
 */
public enum BusinessType {

    OTHER("OTHER", "其他"),
    INSERT("INSERT", "新增"),
    UPDATE("UPDATE", "修改"),
    DELETE("DELETE", "删除"),
    GRANT("GRANT", "授权"),
    EXPORT("EXPORT", "导出"),
    IMPORT("IMPORT", "导入"),
    CLEAN("CLEAN", "清空");

    private final String code;

    private final String desc;

    BusinessType(String code, String desc) {
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
