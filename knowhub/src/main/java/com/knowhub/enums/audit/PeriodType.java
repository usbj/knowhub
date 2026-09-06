package com.knowhub.enums.audit;

/**
 * 报表周期类型枚举，对应字典 audit_period_type 与 audit_period_report.period_type 列。
 * MONTH 月度  每月1日生成上月月报，period_key 格式 "yyyy-MM"（如 "2026-07"）
 * WEEK  周记  每周一生成上周周报，period_key 格式 "yyyy-'W'ww"（如 "2026-W32"，ISO 周历）
 * 枚举值与字典 dict_data_value 一致，供前端筛选与后端校验共用。
 */
public enum PeriodType {

    MONTH("MONTH", "月度"),
    WEEK("WEEK", "周记");

    private final String code;

    private final String label;

    PeriodType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按 code 安全解析枚举，未命中返回 null。
     */
    public static PeriodType ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (PeriodType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}