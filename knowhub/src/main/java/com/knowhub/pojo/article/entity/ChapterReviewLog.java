package com.knowhub.pojo.article.entity;

import java.util.Date;

/**
 * 章节作者审核流水实体，对应 chapter_review_log 表。
 * 照搬 article_review_log / project_review_log 结构，被审对象换 chapter_id。
 * 仅 visibility=SEMIPUBLIC 文章场景触发（非作者提交章节后待文章作者审核），
 * 不受系统审核开关 knowhub.article.review_enabled 影响（是 visibility tier 固有机制，
 * 独立于文章系统审核）。
 *
 * 字段说明：
 * - action 见 {@link com.knowhub.enums.ReviewAction}，但章节作者审核只用 SUBMIT/APPROVE/REJECT
 *   三值（章节无 REVOKE/PUBLISH 直通语义；章节撤回走 editChapter 状态机不进此表）
 * - operatorId 用 userId 稳定锁定操作人，operator 存 username 快照便于直读
 * - role 审核业务身份：AUTHOR=章节提交者，REVIEWER=文章作者审；SYSTEM 不会出现在章节流水
 *
 * 不继承 BaseEntity（流水无 updateBy/updateTime，只有动作时间 createTime）。
 */
public class ChapterReviewLog {

    private Long reviewLogId;

    private Long chapterId;

    private String action;

    private Long operatorId;

    private String operator;

    private String role;

    private String advice;

    private Date createTime;

    /** 操作人昵称（非表字段，由 listByChapterId left join sys_user 带出，供 VO 直接展示） */
    private String operatorNickname;

    public ChapterReviewLog() {
    }

    public ChapterReviewLog(Long chapterId, String action, Long operatorId, String operator, String role, String advice) {
        this.chapterId = chapterId;
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
        return "ChapterReviewLog{" +
                "reviewLogId=" + reviewLogId +
                ", chapterId=" + chapterId +
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
