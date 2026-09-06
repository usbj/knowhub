package com.knowhub.pojo.article.entity;

import java.util.Date;

/**
 * 文章收藏明细实体，对应 article_collect 表。照搬 blog_collect 范式。
 * 主键 (article_id, user_id)；收藏=插入，取消=删除。结构与 ArticleLike 对称。
 */
public class ArticleCollect {

    private Long articleId;

    private Long userId;

    private Date createTime;

    public ArticleCollect() {
    }

    public ArticleCollect(Long articleId, Long userId) {
        this.articleId = articleId;
        this.userId = userId;
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

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}