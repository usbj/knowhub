package com.knowhub.pojo.article.entity;

import com.rookie.common.pojo.BaseEntity;

/**
 * 文章贡献者申请/授权实体，对应 article_contributor 表。
 * 读者申请成为某文章的贡献者，作者审批（PENDING 待审 / APPROVED 已批准 / REJECTED 已驳回）。
 * APPROVED 行供 ChapterServiceImpl.canEditArticle 第三放行分支识别——approved 贡献者可提交
 * 章节，不要求系统编辑级权限 knowhub:article:edit:lN>=level（与作者并列放行）。
 * 审计列由 BaseEntity 承载；软删 deleted 独立字段；apply_by/handle_by 存 username 快照（照全项目约定）。
 * 非表字段：articleTitle（join article 带出）、nickname（join sys_user 带出，申请者昵称）、
 *           articleNickname（join sys_user 带出，文章作者昵称）—— 供列表展示拼接通知文案。
 */
public class ArticleContributor extends BaseEntity {

    private Long contributorId;

    private Long articleId;

    private Long userId;

    /** 申请状态：PENDING/APPROVED/REJECTED（见 ArticleContributorStatus 枚举，不入字典） */
    private String status;

    /** 驳回原因（仅 REJECTED 时填） */
    private String advice;

    /** 申请者 username 快照 */
    private String applyBy;

    private java.util.Date applyTime;

    /** 审批人 username 快照（文章作者） */
    private String handleBy;

    private java.util.Date handleTime;

    private Integer deleted;

    // ---- 非表字段（列表 join 带出） ----
    /** 文章标题（join article 带出，供通知文案与列表展示） */
    private String articleTitle;

    /** 申请者昵称（join sys_user on user_id=user_id 带出） */
    private String nickname;

    /** 文章作者昵称（join sys_user 带出，供申请人侧展示审批人） */
    private String articleNickname;

    public ArticleContributor() {
    }

    public Long getContributorId() {
        return contributorId;
    }

    public void setContributorId(Long contributorId) {
        this.contributorId = contributorId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public String getApplyBy() {
        return applyBy;
    }

    public void setApplyBy(String applyBy) {
        this.applyBy = applyBy;
    }

    public java.util.Date getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(java.util.Date applyTime) {
        this.applyTime = applyTime;
    }

    public String getHandleBy() {
        return handleBy;
    }

    public void setHandleBy(String handleBy) {
        this.handleBy = handleBy;
    }

    public java.util.Date getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(java.util.Date handleTime) {
        this.handleTime = handleTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getArticleTitle() {
        return articleTitle;
    }

    public void setArticleTitle(String articleTitle) {
        this.articleTitle = articleTitle;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getArticleNickname() {
        return articleNickname;
    }

    public void setArticleNickname(String articleNickname) {
        this.articleNickname = articleNickname;
    }
}