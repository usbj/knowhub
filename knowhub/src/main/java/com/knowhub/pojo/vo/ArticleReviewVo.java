package com.knowhub.pojo.vo;

/**
 * 文章系统审核入参 VO（与项目 ProjectReviewVo 结构同，字段名 article 化）。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 * 仅 PENDING_REVIEW 态可审；审核员与作者为同一人时回避（author_id 比对）。
 */
public class ArticleReviewVo {

    private Long articleId;

    private Boolean pass;

    private String advice;

    public ArticleReviewVo() {
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Boolean getPass() {
        return pass;
    }

    public void setPass(Boolean pass) {
        this.pass = pass;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    @Override
    public String toString() {
        return "ArticleReviewVo{" +
                "articleId=" + articleId +
                ", pass=" + pass +
                ", advice='" + advice + '\'' +
                '}';
    }
}
