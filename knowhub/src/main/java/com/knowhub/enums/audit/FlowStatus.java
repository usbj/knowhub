package com.knowhub.enums.audit;

/**
 * 资金流水状态枚举，对应字典 audit_flow_status 与 audit_fund_flow.status 列。
 * 仅 EXPENSE 用全状态流转；BUDGET/INCOME 恒 APPROVED（只记账留痕不走审批）。
 *   DRAFT    草稿       花销未提交审批
 *   PENDING  待审核     高于阈值花销提交后待审
 *   APPROVED 已通过     低阈值自动通过/审核员通过/BUDGET/INCOME 恒此态
 *   REJECTED 已驳回     审核员驳回
 *   REVOKED  已撤回     作者撤回已通过花销
 * 与 review_status(NONE/PENDING/APPROVED/REJECTED) 正交：status 是业务状态机，
 * review_status 是审核滋味标记（复用初稿博客/资源字典 review_status）。
 */
public enum FlowStatus {

    DRAFT("DRAFT", "草稿"),
    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    REVOKED("REVOKED", "已撤回");

    private final String code;

    private final String label;

    FlowStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static FlowStatus ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (FlowStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}