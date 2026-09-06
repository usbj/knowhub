package com.knowhub.enums.common;

/**
 * 博客/资源审核流水动作，对应字典 review_action 与 blog_review_log/resource_review_log.action 列。
 * 字典 blog_review_action 已通用化为 review_action（博客+资源共用，见 sql/knowhub-resource.sql）。
 * action 隐含文章状态转移语义（流水只记动作不记状态前后）：
 *   SUBMIT  作者提交审核   DRAFT/REJECTED/REVOKED → PENDING_REVIEW
 *   APPROVE 审核员通过     PENDING_REVIEW → PUBLISHED
 *   REJECT  审核员驳回     PENDING_REVIEW → REJECTED
 *   REVOKE  作者撤回       PUBLISHED → REVOKED
 *   PUBLISH 系统直通发布   审核开关关时 DRAFT/REJECTED/REVOKED → PUBLISHED
 */
public enum ReviewAction {

    SUBMIT("SUBMIT", "提交审核", "AUTHOR"),
    APPROVE("APPROVE", "通过", "REVIEWER"),
    REJECT("REJECT", "驳回", "REVIEWER"),
    REVOKE("REVOKE", "撤回", "AUTHOR"),
    PUBLISH("PUBLISH", "直通发布", "SYSTEM");

    private final String code;

    private final String desc;

    /** 该动作对应的审核业务身份（AUTHOR/REVIEWER/SYSTEM），按动作类型定，非系统角色 */
    private final String role;

    ReviewAction(String code, String desc, String role) {
        this.code = code;
        this.desc = desc;
        this.role = role;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public String getRole() {
        return role;
    }
}
