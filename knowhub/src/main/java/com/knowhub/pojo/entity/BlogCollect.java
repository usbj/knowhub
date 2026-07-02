package com.knowhub.pojo.entity;

import java.util.Date;

/**
 * 博客收藏明细实体，对应 blog_collect 表。
 * 主键 (blog_id, user_id)；收藏=插入，取消=删除。结构与 BlogLike 对称。
 */
public class BlogCollect {

    private Long blogId;

    private Long userId;

    private Date createTime;

    public BlogCollect() {
    }

    public BlogCollect(Long blogId, Long userId) {
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
        return "BlogCollect{" +
                "blogId=" + blogId +
                ", userId=" + userId +
                ", createTime=" + createTime +
                '}';
    }
}