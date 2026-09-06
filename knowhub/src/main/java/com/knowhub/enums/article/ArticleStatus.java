package com.knowhub.enums.article;

/**
 * 文章状态，对应字典 article_status 与 article.status 列。
 * 值同博客 status 语义：DRAFT 草稿 / PUBLISHED 已发布 / REVOKED 已撤回 /
 * PENDING_REVIEW 待审核 / REJECTED 已驳回（项目 ARCHIVED 归档态文章不设）。
 * 审核状态机前置校验照搬博客：
 *   publish 合法前置 {DRAFT, REJECTED, REVOKED}（开关开转 PENDING_REVIEW，关转 PUBLISHED）
 *   revoke  合法前置 {PUBLISHED}（转 REVOKED）
 *   review  合法前置 {PENDING_REVIEW}（pass 转 PUBLISHED，reject 转 REJECTED）
 *   edit    合法前置 {DRAFT, REJECTED, REVOKED}（PUBLISHED 禁编须先 revoke，防绕审改已发布）
 */
public enum ArticleStatus {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    REVOKED("REVOKED", "已撤回"),
    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    REJECTED("REJECTED", "已驳回");

    private final String code;

    private final String desc;

    ArticleStatus(String code, String desc) {
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
