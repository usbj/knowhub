package com.knowhub.pojo.entity;

import java.util.Date;

/**
 * 博客-标签关联实体，对应 blog_tag 表（多对多中间表）。
 * 轻量 POJO，无审计列；编辑文章时先删后插重建。
 */
public class BlogTag {

    private Long blogId;

    private Long tagId;

    private Date createTime;

    public BlogTag() {
    }

    public BlogTag(Long blogId, Long tagId) {
        this.blogId = blogId;
        this.tagId = tagId;
    }

    public Long getBlogId() {
        return blogId;
    }

    public void setBlogId(Long blogId) {
        this.blogId = blogId;
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
        return "BlogTag{" +
                "blogId=" + blogId +
                ", tagId=" + tagId +
                ", createTime=" + createTime +
                '}';
    }
}