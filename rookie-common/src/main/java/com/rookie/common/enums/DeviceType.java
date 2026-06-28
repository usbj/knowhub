package com.rookie.common.enums;

/**
 * 操作日志设备类型（UA 解析）
 * 对应字典 sys_oper_device_type，code 值与字典 dict_data_value 一致
 */
public enum DeviceType {

    PC("PC", "PC端"),
    MOBILE("MOBILE", "移动端"),
    TABLET("TABLET", "平板"),
    UNKNOWN("UNKNOWN", "未知");

    private final String code;

    private final String desc;

    DeviceType(String code, String desc) {
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
