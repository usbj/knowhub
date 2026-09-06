package com.knowhub.pojo.audit.entity;

import java.util.Date;

/**
 * 借出审批流水实体，对应 audit_loan_review_log 表。
 * 流水表只追加不改不删，记全量审核历史；被审对象是 audit_loan.loan_id
 * （照 audit_flow_review_log / blog_review_log 范式，仅关联键换 loanId）。
 *
 * 字段说明：
 * - action 复用 ReviewAction(SUBMIT/APPROVE/REJECT) 三值（借出无 revoke 直通语义）。
 *   新增借出记 SUBMIT；审批通过记 APPROVE；驳回记 REJECT。
 * - operatorId 用 userId 稳定锁定操作人，operator 存 username 快照便于直读。
 * - role 审核业务身份(AUTHOR/REVIEWER)，按动作类型定（来自 ReviewAction.getRole）。
 *
 * 不继承 BaseEntity（流水无 updateBy/updateTime，只有动作时间 createTime）。
 */
public class AuditLoanReviewLog {

    private Long reviewLogId;

    private Long loanId;

    private String action;

    private Long operatorId;

    private String operator;

    private String role;

    private String advice;

    private Date createTime;

    /** 操作人昵称（非表字段，由 listByLoanId left join sys_user 带出，供 VO 直接展示） */
    private String operatorNickname;

    public AuditLoanReviewLog() {
    }

    /** 便捷构造器：service 层写流水用（review_log_id/createTime 由 DB 填充） */
    public AuditLoanReviewLog(Long loanId, String action, Long operatorId, String operator, String role, String advice) {
        this.loanId = loanId;
        this.action = action;
        this.operatorId = operatorId;
        this.operator = operator;
        this.role = role;
        this.advice = advice;
    }

    public Long getReviewLogId() {
        return reviewLogId;
    }

    public void setReviewLogId(Long reviewLogId) {
        this.reviewLogId = reviewLogId;
    }

    public Long getLoanId() {
        return loanId;
    }

    public void setLoanId(Long loanId) {
        this.loanId = loanId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getOperatorNickname() {
        return operatorNickname;
    }

    public void setOperatorNickname(String operatorNickname) {
        this.operatorNickname = operatorNickname;
    }

    @Override
    public String toString() {
        return "AuditLoanReviewLog{" +
                "reviewLogId=" + reviewLogId +
                ", loanId=" + loanId +
                ", action='" + action + '\'' +
                ", operatorId=" + operatorId +
                ", operator='" + operator + '\'' +
                ", role='" + role + '\'' +
                ", advice='" + advice + '\'' +
                ", createTime=" + createTime +
                ", operatorNickname='" + operatorNickname + '\'' +
                '}';
    }
}