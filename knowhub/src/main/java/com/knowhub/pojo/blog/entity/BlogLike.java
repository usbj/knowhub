package com.knowhub.pojo.blog.entity;

import java.util.Date;

/**
 * 博客点赞明细实体，对应 blog_like 表。
 * 主键 (blog_id, user_id)；点赞=插入，取消=删除。
 */
public class BlogLike {

    private Long blogId;

    private Long userId;

    private Date createTime;

    public BlogLike() {
    }

    public BlogLike(Long blogId, Long userId) {
        this.blogId = blogId;
        this.userId = userId;
    }

    public Long getBlogId() {
        return blogId;
    }

    public void setBlogId(Long blogId) {
        this.blogId = blogId;
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

    @Override
    public String toString() {
        return "BlogLike{" +
                "blogId=" + blogId +
                ", userId=" + userId +
                ", createTime=" + createTime +
                '}';
    }
}