package com.knowhub.enums.audit;

/**
 * 资金流水类型枚举，对应字典 audit_flow_type 与 audit_fund_flow.flow_type 列。
 * 三流合一记同一张流表，flow_type 区分资金进/出方向与审批策略：
 *   BUDGET  预算注入   计划额度，累加 subject.budget_total，未必到账，免审(status 恒 APPROVED)
 *   INCOME  收账到账   实到钱，累加 subject.income_total，真金白银，免审(status 恒 APPROVED)
 *   EXPENSE 花销支出   走阈值审批(低于 sys_config 阈值自动 APPROVED，高于走 PENDING→审核)，
 *                      APPROVED 时聚合计入余额扣减(income_total - 历史APPROVED EXPENSE 合计)
 * 枚举值与字典 dict_data_value 一致，供前端下拉与后端校验共用。
 */
public enum FlowType {

    BUDGET("BUDGET", "预算"),
    INCOME("INCOME", "收账"),
    EXPENSE("EXPENSE", "花销");

    private final String code;

    private final String label;

    FlowType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static FlowType ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (FlowType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}