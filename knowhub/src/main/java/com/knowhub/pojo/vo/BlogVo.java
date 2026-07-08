package com.knowhub.pojo.vo;

import java.util.Date;
import java.util.List;

/**
 * 博客文章对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不要用 String），序列化由全局 jackson.date-format
 * 统一格式化为 yyyy-MM-dd HH:mm:ss（见 application.yml），前端用 formatDateTime 展示。
 * tagIds 为作者选用的受控标签 id 列表；tagNames 由关联表回填用于展示。
 * authorId 为作者用户ID(userId)，与 createBy(username) 互补，前台展示昵称走 join sys_user。
 */
public class BlogVo {

    private Long blogId;

    private Long authorId;

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

    private List<Long> tagIds;

    private List<String> tagNames;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    /** 当前登录用户是否已点赞（详情接口回填） */
    private Boolean hasLiked;

    /** 当前登录用户是否已收藏（详情接口回填） */
    private Boolean hasCollected;

    public BlogVo() {
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

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }

    public List<String> getTagNames() {
        return tagNames;
    }

    public void setTagNames(List<String> tagNames) {
        this.tagNames = tagNames;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean getHasLiked() {
        return hasLiked;
    }

    public void setHasLiked(Boolean hasLiked) {
        this.hasLiked = hasLiked;
    }

    public Boolean getHasCollected() {
        return hasCollected;
    }

    public void setHasCollected(Boolean hasCollected) {
        this.hasCollected = hasCollected;
    }

    @Override
    public String toString() {
        return "BlogVo{" +
                "blogId=" + blogId +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", publishTime='" + publishTime + '\'' +
                ", viewCount=" + viewCount +
                ", likeCount=" + likeCount +
                ", collectCount=" + collectCount +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", tagIds=" + tagIds +
                ", tagNames=" + tagNames +
                ", hasLiked=" + hasLiked +
                ", hasCollected=" + hasCollected +
                '}';
    }
}