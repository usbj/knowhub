package com.knowhub.enums;

/**
 * 文章可见性，对应字典 article_visibility 与 article.visibility 列。
 * 决定章节提交走不走文章作者审核（与文章等级正交：等级管外部可见，可见性管章节提交策略）：
 *   PRIVATE     未公开 —— 仅作者能写章节，章节提交免审直接 PUBLISHED
 *   SEMIPUBLIC  半公开 —— 有文章更改权限者(系统 edit:lN≥level 或作者)可提交章节，
 *                         提交后需文章作者审核（走 chapter_review_log）
 *   PUBLIC      全公开 —— 有文章更改权限者可提交章节，提交后直接 PUBLISHED 免审
 * 此三档是文章内部编辑权限策略，不受系统审核开关 knowhub.article.review_enabled 影响
 * （系统审核管文章对外发布，可见性管章节提交审核，两套机制独立）。
 */
public enum ArticleVisibility {

    PRIVATE("PRIVATE", "未公开"),
    SEMIPUBLIC("SEMIPUBLIC", "半公开"),
    PUBLIC("PUBLIC", "全公开");

    private final String code;

    private final String desc;

    ArticleVisibility(String code, String desc) {
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
