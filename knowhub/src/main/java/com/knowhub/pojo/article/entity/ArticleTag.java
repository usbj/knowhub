package com.knowhub.pojo.article.entity;

import java.util.Date;

/**
 * 文章-标签关联实体，对应 article_tag 表（照搬 blog_tag 结构，多对多中间表）。
 * 文章复用既有 tag 表（不新建 tag），仅建文章侧关联。轻量 POJO，无审计列；编辑文章时先删后插重建。
 */
public class ArticleTag {

    private Long articleId;

    private Long tagId;

    private Date createTime;

    public ArticleTag() {
    }

    public ArticleTag(Long articleId, Long tagId) {
        this.articleId = articleId;
        this.tagId = tagId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "ArticleTag{" +
                "articleId=" + articleId +
                ", tagId=" + tagId +
                ", createTime=" + createTime +
                '}';
    }
}
