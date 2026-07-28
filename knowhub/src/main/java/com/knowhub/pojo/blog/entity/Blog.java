package com.knowhub.pojo.blog.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 博客文章实体，对应 blog 表。
 * 模块本身即"博客(blog)"，文章实体直接称 Blog（模块命名详见 doc/blog/blog-module-design.md）。
 * 审计列(createBy/updateBy/createTime/updateTime) 由 BaseEntity 承载；软删 deleted 独立字段。
 * authorId 为作者用户ID(userId)，与审计列 createBy(username) 互补：
 *   createBy 存账号串便于直接展示，authorId 用 userId 稳定锁定（username 可改，userId 不变），
 *   前台展示作者昵称走 join sys_user on user_id=author_id，username 变动不影响。
 */
public class Blog extends BaseEntity {

    private Long blogId;

    private Long authorId;

    /** 博客等级 1公开/2内部/3机密（见 BlogLevel 枚举，对标系统 view/edit:lN 权限等级） */
    private Integer level;

    private String title;

    private String content;

    private String summary;

    private String coverUrl;

    private String status;

    private Date publishTime;

    private Long viewCount;

    private Long likeCount;

    private Long collectCount;

    private String reviewStatus;

    private String reviewer;

    private Date reviewTime;

    private String reviewAdvice;

    private Integer deleted;

    public Blog() {
    }

    public Blog(Date createTime, Date updateTime, String createBy, String updateBy,
                Long blogId, Long authorId, Integer level, String title, String content, String summary, String coverUrl,
                String status, Date publishTime, Long viewCount, Long likeCount, Long collectCount,
                String reviewStatus, String reviewer, Date reviewTime, String reviewAdvice, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.blogId = blogId;
        this.authorId = authorId;
        this.level = level;
        this.title = title;
        this.content = content;
        this.summary = summary;
        this.coverUrl = coverUrl;
        this.status = status;
        this.publishTime = publishTime;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.collectCount = collectCount;
        this.reviewStatus = reviewStatus;
        this.reviewer = reviewer;
        this.reviewTime = reviewTime;
        this.reviewAdvice = reviewAdvice;
        this.deleted = deleted;
    }

    public Long getBlogId() {
        return blogId;
    }

    public void setBlogId(Long blogId) {
        this.blogId = blogId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Date publishTime) {
        this.publishTime = publishTime;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public Long getCollectCount() {
        return collectCount;
    }

    public void setCollectCount(Long collectCount) {
        this.collectCount = collectCount;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public Date getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime) {
        this.reviewTime = reviewTime;
    }

    public String getReviewAdvice() {
        return reviewAdvice;
    }

    public void setReviewAdvice(String reviewAdvice) {
        this.reviewAdvice = reviewAdvice;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "Blog{" +
                "blogId=" + blogId +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", publishTime=" + publishTime +
                ", viewCount=" + viewCount +
                ", likeCount=" + likeCount +
                ", collectCount=" + collectCount +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", deleted=" + deleted +
                ", createTime=" + getCreateTime() +
                ", updateTime=" + getUpdateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                '}';
    }
}