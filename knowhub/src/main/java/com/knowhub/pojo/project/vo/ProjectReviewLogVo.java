package com.knowhub.pojo.project.vo;

import java.util.Date;

/**
 * 项目审核流水对外 VO，供审核历史接口出参。
 * 与资源 ResourceReviewLogVo 结构同（字段名 project 化），时间字段一律用 java.util.Date
 * （不要用 String），序列化由全局 jackson.date-format 统一格式化。
 * operatorNickname 由后端 join sys_user 带出，供前端直接展示操作人昵称，
 * 不必前端再二次查询；operator(username) 也保留作账号快照。
 * action/role 复用 ReviewAction 枚举 + review_action 字典（博客/资源/项目共用）。
 */
public class ProjectReviewLogVo {

    private Long reviewLogId;

    private Long projectId;

    /** 审核动作 code：SUBMIT/APPROVE/REJECT/REVOKE/PUBLISH（见字典 review_action，复用） */
    private String action;

    private Long operatorId;

    /** 操作人用户名快照 */
    private String operator;

    /** 操作人昵称（后端 join sys_user 带出，便于前端展示） */
    private String operatorNickname;

    /** 审核业务身份：AUTHOR/REVIEWER/SYSTEM */
    private String role;

    private String advice;

    private Date createTime;

    public ProjectReviewLogVo() {
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
        return "ProjectReviewLogVo{" +
                "reviewLogId=" + reviewLogId +
                ", projectId=" + projectId +
                ", action='" + action + '\'' +
                ", operator='" + operator + '\'' +
                ", operatorNickname='" + operatorNickname + '\'' +
                ", role='" + role + '\'' +
                ", createTime='" + createTime + '\'' +
                '}';
    }
}
