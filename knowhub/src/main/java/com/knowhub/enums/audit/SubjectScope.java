package com.knowhub.enums.audit;

/**
 * 花销主体范围枚举，对应字典 audit_subject_scope 与 audit_subject.scope 列。
 * LAB：实验室级总账（奖金池、外部捐赠汇总，整个实验室的资金池）。
 * PROJECT：项目/赛事级子账（某次比赛单独拨的预算子账，scope=PROJECT 时 project_id 关联赛事）。
 * 枚举值与字典 dict_data_value 一致，供前端下拉与后端校验共用。
 */
public enum SubjectScope {

    LAB("LAB", "实验室"),
    PROJECT("PROJECT", "项目赛事");

    private final String code;

    private final String label;

    SubjectScope(String code, String label) {
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
    public static SubjectScope ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (SubjectScope s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}