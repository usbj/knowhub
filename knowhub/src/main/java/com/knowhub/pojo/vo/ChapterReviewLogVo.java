package com.knowhub.pojo.vo;

import java.util.Date;

/**
 * 章节作者审核流水对外 VO，供章节审核历史接口出参。
 * 照搬 ArticleReviewLogVo 结构（字段名 chapter 化），时间字段一律用 java.util.Date。
 * operatorNickname 由后端 join sys_user 带出，供前端直接展示操作人昵称。
 * action 仅 SUBMIT/APPROVE/REJECT 三值（章节作者审核场景，无 REVOKE/PUBLISH）；
 * role 取 AUTHOR(章节提交者)/REVIEWER(文章作者审)，SYSTEM 不会出现在章节流水。
 * action/role 复用 ReviewAction 枚举 + review_action 字典。
 */
public class ChapterReviewLogVo {

    private Long reviewLogId;

    private Long chapterId;

    /** 审核动作 code：SUBMIT/APPROVE/REJECT（见字典 review_action，复用三值） */
    private String action;

    private Long operatorId;

    /** 操作人用户名快照 */
    private String operator;

    /** 操作人昵称（后端 join sys_user 带出，便于前端展示） */
    private String operatorNickname;

    /** 审核业务身份：AUTHOR章节提交者/REVIEWER文章作者审 */
    private String role;

    private String advice;

    private Date createTime;

    public ChapterReviewLogVo() {
    }

    public Long getReviewLogId() {
        return reviewLogId;
    }

    public void setReviewLogId(Long reviewLogId) {
        this.reviewLogId = reviewLogId;
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
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
        return "ChapterReviewLogVo{" +
                "reviewLogId=" + reviewLogId +
                ", chapterId=" + chapterId +
                ", action='" + action + '\'' +
                ", operator='" + operator + '\'' +
                ", operatorNickname='" + operatorNickname + '\'' +
                ", role='" + role + '\'' +
                ", createTime='" + createTime + '\'' +
                '}';
    }
}
