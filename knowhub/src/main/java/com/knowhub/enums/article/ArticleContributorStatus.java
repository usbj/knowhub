package com.knowhub.enums.article;

/**
 * 文章贡献者申请状态，对应 article_contributor.status 列。
 * 文章贡献流程：读者申请成为贡献者 → 作者审批 → APPROVED 后可提交章节。
 * 三态枚举不入字典（前端 inline 映射文案，照 ChapterStatus 口径）：
 *   PENDING  待审           作者尚未处理
 *   APPROVED 已批准         贡献者可提交章节（ChapterServiceImpl.canEditArticle 第三放行分支）
 *   REJECTED 已驳回         作者附驳回原因 advice，贡献者可改后重申（旧行软删再插新）
 */
public enum ArticleContributorStatus {

    PENDING("PENDING", "待审"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已驳回");

    private final String code;

    private final String desc;

    ArticleContributorStatus(String code, String desc) {
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