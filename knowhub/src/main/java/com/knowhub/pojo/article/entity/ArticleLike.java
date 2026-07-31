package com.knowhub.pojo.article.entity;

import java.util.Date;

/**
 * 文章点赞明细实体，对应 article_like 表。照搬 blog_like 范式。
 * 主键 (article_id, user_id)；点赞=插入，取消=删除。
 */
public class ArticleLike {

    private Long articleId;

    private Long userId;

    private Date createTime;

    public ArticleLike() {
    }

    public ArticleLike(Long articleId, Long userId) {
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