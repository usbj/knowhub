package com.knowhub.pojo.entity;

import java.util.Date;

/**
 * 项目审核流水实体，对应 project_review_log 表。
 * 结构与 resource_review_log / blog_review_log 完全同构：只追加不改不删，记全量审核历史；
 * 主表只存状态机当前值，流水记全量轨迹。只记动作不记状态前后(action 隐含转移语义)。
 *
 * 字段说明：
 * - action 见 {@link com.knowhub.enums.ReviewAction}（代码层枚举复用，字典 review_action
 *   博客/资源/项目共用 dict_id=25），隐含项目状态转移语义
 * - operatorId 用 userId 稳定锁定操作人，operator 存 username 快照便于直读
 * - role 审核业务身份(AUTHOR/REVIEWER/SYSTEM)，按动作类型定，非系统角色(sys_role)
 *
 * 不继承 BaseEntity（流水无 updateBy/updateTime，只有动作时间 createTime）。
 */
public class ProjectReviewLog {

    private Long reviewLogId;

    private Long projectId;

    private String action;

    private Long operatorId;

    private String operator;

    private String role;

    private String advice;

    private Date createTime;

    /** 操作人昵称（非表字段，由 listByProjectId left join sys_user 带出，供 VO 直接展示） */
    private String operatorNickname;

    public ProjectReviewLog() {
    }

    public ProjectReviewLog(Long projectId, String action, Long operatorId, String operator, String role, String advice) {
        this.projectId = projectId;
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

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
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
        return "ProjectReviewLog{" +
                "reviewLogId=" + reviewLogId +
                ", projectId=" + projectId +
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
