package com.knowhub.pojo.audit.entity;

import java.util.Date;

/**
 * 花销审批流水实体，对应 audit_flow_review_log 表。
 * 流水表只追加不改不删，记全量审核历史；被审对象是 audit_fund_flow.flow_id
 * （照 blog_review_log/resource_review_log 范式，仅关联键换 flowId）。
 *
 * 字段说明：
 * - action 见 {@link com.knowhub.enums.common.ReviewAction}，复用 review_action 字典(dict_id=25)。
 *   阈值审批低于阈值自动 APPROVED 时记 SUBMIT+APPROVE 两条；BUDGET/INCOME 写入时各记一条 SUBMIT。
 * - operatorId 用 userId 稳定锁定操作人，operator 存 username 快照便于直读。
 * - role 审核业务身份(AUTHOR/REVIEWER/SYSTEM)，按动作类型定（来自 ReviewAction.getRole），非系统角色(sys_role)。
 *
 * 不继承 BaseEntity（流水无 updateBy/updateTime，只有动作时间 createTime）。
 */
public class AuditFlowReviewLog {

    private Long reviewLogId;

    private Long flowId;

    private String action;

    private Long operatorId;

    private String operator;

    private String role;

    private String advice;

    private Date createTime;

    /** 操作人昵称（非表字段，由 listByFlowId left join sys_user 带出，供 VO 直接展示） */
    private String operatorNickname;

    public AuditFlowReviewLog() {
    }

    /** 便捷构造器：service 层写流水用（review_log_id/createTime 由 DB 填充） */
    public AuditFlowReviewLog(Long flowId, String action, Long operatorId, String operator, String role, String advice) {
        this.flowId = flowId;
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

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
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
        return "AuditFlowReviewLog{" +
                "reviewLogId=" + reviewLogId +
                ", flowId=" + flowId +
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