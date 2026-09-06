package com.knowhub.enums.audit;

/**
 * 借出状态枚举，对应字典 audit_loan_status 与 audit_loan.status 列。
 * 借出状态流转：
 *   REQUEST    申请待审  走审批时新增态（loan_approval_enabled=true）
 *   BORROWED   已借出    审批通过 / 免审(loan_approval_enabled=false)直接此态
 *   RETURNED   已归还    归还后回填 actual_return_date
 *   OVERDUE    逾期     AuditLoanOverdueTask 对账扫出(expected_return_date < now AND status=BORROWED)
 *   REJECTED   已驳回    审批驳回（仅 REQUEST 态可驳回）
 *
 * 流转图：
 *   开启审批：新增 REQUEST →(approve)→ BORROWED →(return)→ RETURNED
 *                       →(reject)→ REJECTED
 *   免审：新增直接 BORROWED →(return)→ RETURNED
 *   逾期：BORROWED + 超期 →(定时任务)→ OVERDUE →(return)→ RETURNED
 *
 * 与 review_status(NONE/PENDING/APPROVED/REJECTED) 区别：本枚举是借出业务状态机，
 * review_status 是审核滋味标记，audit_loan 表不另设 review_status 列（status 已足够表达）。
 */
public enum LoanStatus {

    REQUEST("REQUEST", "申请待审"),
    BORROWED("BORROWED", "已借出"),
    RETURNED("RETURNED", "已归还"),
    OVERDUE("OVERDUE", "逾期"),
    REJECTED("REJECTED", "已驳回");

    private final String code;

    private final String label;

    LoanStatus(String code, String label) {
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
    public static LoanStatus ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (LoanStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}