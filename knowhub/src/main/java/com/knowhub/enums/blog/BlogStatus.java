package com.knowhub.enums.blog;

/**
 * 博客文章状态，对应字典 blog_status 与 blog.status 列。
 * DRAFT 草稿 / PUBLISHED 已发布 / REVOKED 已撤回 / PENDING_REVIEW 待审核 / REJECTED 已驳回
 */
public enum BlogStatus {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    REVOKED("REVOKED", "已撤回"),
    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    REJECTED("REJECTED", "已驳回");

    private final String code;

    private final String desc;

    BlogStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}