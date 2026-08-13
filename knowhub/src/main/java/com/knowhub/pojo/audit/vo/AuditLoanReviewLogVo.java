package com.knowhub.pojo.audit.vo;

import java.util.Date;

/**
 * 借出审批流水对外 VO，供审核历史接口出参（详情页审核记录折叠区）。
 * 与 AuditFlowReviewLogVo 同构，仅关联键换 loanId（本模块以 loanId 关联）。
 * 时间字段一律用 java.util.Date（不用 String），序列化由全局 jackson.date-format 统一格式化。
 * operatorNickname 由后端 join sys_user 带出，供前端直接展示操作人昵称，
 * 不必前端再二次查询；operator(username) 也保留作账号快照。
 */
public class AuditLoanReviewLogVo {

    private Long reviewLogId;

    private Long loanId;

    /** 审核动作 code：SUBMIT/APPROVE/REJECT（借出复用 review_action 字典三值，无 REVOKE） */
    private String action;

    private Long operatorId;

    /** 操作人用户名快照 */
    private String operator;

    /** 操作人昵称（后端 join sys_user 带出，便于前端展示） */
    private String operatorNickname;

    /** 审核业务身份：AUTHOR/REVIEWER */
    private String role;

    private String advice;

    private Date createTime;

    public AuditLoanReviewLogVo() {
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

    public String getOperatorNickname() {
        return operatorNickname;
    }

    public void setOperatorNickname(String operatorNickname) {
        this.operatorNickname = operatorNickname;
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

    @Override
    public String toString() {
        return "AuditLoanReviewLogVo{" +
                "reviewLogId=" + reviewLogId +
                ", loanId=" + loanId +
                ", action='" + action + '\'' +
                ", operator='" + operator + '\'' +
                ", operatorNickname='" + operatorNickname + '\'' +
                ", role='" + role + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}