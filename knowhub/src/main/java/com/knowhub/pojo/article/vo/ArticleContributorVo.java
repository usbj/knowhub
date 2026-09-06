package com.knowhub.pojo.article.vo;

import java.util.Date;

/**
 * 文章贡献者申请/授权展示 VO（前台「我的协作」页列表用）。
 * 字段口径：照 ArticleContributor 实体，脱去审计列（createTime/updateTime/createBy/updateBy）
 * 与 deleted（前台不感知软删）；join 字段 articleTitle/nickname/articleNickname 保留供前端展示。
 * status 用前端 inline 映射文案（PENDING/APPROVED/REJECTED 照 chapters.vue 的 statusMeta 口径）。
 */
public class ArticleContributorVo {

    private Long contributorId;

    private Long articleId;

    private Long userId;

    private String status;

    private String advice;

    private String applyBy;

    private Date applyTime;

    private String handleBy;

    private Date handleTime;

    // ---- join 带出 ----
    private String articleTitle;

    private String nickname;

    private String articleNickname;

    /** 文章作者 userId（join article 带出，前端申请按钮态判定用） */
    private Long articleAuthorId;

    public ArticleContributorVo() {
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

    public Date getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(Date applyTime) {
        this.applyTime = applyTime;
    }

    public String getHandleBy() {
        return handleBy;
    }

    public void setHandleBy(String handleBy) {
        this.handleBy = handleBy;
    }

    public Date getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(Date handleTime) {
        this.handleTime = handleTime;
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

    public Long getArticleAuthorId() {
        return articleAuthorId;
    }

    public void setArticleAuthorId(Long articleAuthorId) {
        this.articleAuthorId = articleAuthorId;
    }
}