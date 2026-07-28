package com.knowhub.pojo.blog.entity;

import java.util.Date;

/**
 * 博客审核流水实体，对应 blog_review_log 表。
 * 流水表只追加不改不删，记全量审核历史；主表 blog 的审核字段(reviewer/review_time/
 * review_advice/review_status)保留为「当前快照」便于列表展示，流水表记全量轨迹。
 *
 * 字段说明：
 * - action 见 {@link com.knowhub.enums.ReviewAction}，隐含文章状态转移语义
 * - operatorId 用 userId 稳定锁定操作人，operator 存 username 快照便于直读
 * - role 审核业务身份(AUTHOR/REVIEWER/SYSTEM)，按动作类型定，非系统角色(sys_role)
 *
 * 不继承 BaseEntity（流水无 updateBy/updateTime，只有动作时间 createTime）。
 */
public class BlogReviewLog {

    private Long reviewLogId;

    private Long blogId;

    private String action;

    private Long operatorId;

    private String operator;

    private String role;

    private String advice;

    private Date createTime;

    /** 操作人昵称（非表字段，由 listByBlogId left join sys_user 带出，供 VO 直接展示） */
    private String operatorNickname;

    public BlogReviewLog() {
    }

    public BlogReviewLog(Long blogId, String action, Long operatorId, String operator, String role, String advice) {
        this.blogId = blogId;
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

    public Long getBlogId() {
        return blogId;
    }

    public void setBlogId(Long blogId) {
        this.blogId = blogId;
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
        return "BlogReviewLog{" +
                "reviewLogId=" + reviewLogId +
                ", blogId=" + blogId +
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
