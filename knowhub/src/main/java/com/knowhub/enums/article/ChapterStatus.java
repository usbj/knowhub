package com.knowhub.enums.article;

/**
 * 章节状态，对应字典 chapter_status 与 chapter.status 列。
 * 章节状态机比文章多一个 PENDING_AUTHOR_REVIEW（仅 visibility=SEMIPUBLIC 文章触发）：
 *   DRAFT                   草稿（章节作者新建未提交）
 *   PENDING_AUTHOR_REVIEW   待作者审核（半公开文章：非作者提交后待文章作者审）
 *   PUBLISHED               已发布（直通或作者审通过）
 *   REJECTED                已驳回（文章作者驳回，章节作者改后可再提交）
 *   REVOKED                 已撤回（作者撤回已发布章节，改后可再发布）
 * 章节提交状态转移规则（由 visibility + 提交者是否文章作者共同决定，见 ChapterServiceImpl）：
 *   作者本人提交（任意 visibility）        DRAFT → PUBLISHED 免审
 *   非作者提交 PRIVATE 文章                拒绝提交（无章节编辑权）
 *   非作者提交 SEMIPUBLIC 文章             DRAFT → PENDING_AUTHOR_REVIEW → 作者审 → PUBLISHED/REJECTED
 *   非作者提交 PUBLIC 文章                 DRAFT → PUBLISHED 免审
 * PUBLISHED 禁编须先 revoke 再改（对齐博客/文章 PUBLISHED 禁编范式，防绕审改已发布）。
 */
public enum ChapterStatus {

    DRAFT("DRAFT", "草稿"),
    PENDING_AUTHOR_REVIEW("PENDING_AUTHOR_REVIEW", "待作者审核"),
    PUBLISHED("PUBLISHED", "已发布"),
    REJECTED("REJECTED", "已驳回"),
    REVOKED("REVOKED", "已撤回");

    private final String code;

    private final String desc;

    ChapterStatus(String code, String desc) {
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
