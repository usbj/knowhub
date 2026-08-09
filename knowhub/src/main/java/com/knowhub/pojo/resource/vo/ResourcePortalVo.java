package com.knowhub.pojo.resource.vo;

import java.util.Date;

/**
 * 前台资源轻量 VO（列表/搜索/推荐/相关推荐出参）。
 * <p>
 * 含 authorNickname（join sys_user on user_id=author_id 回填，资源主表已有 author_id 列）、
 * categoryName（join resource_category 带出；-1=其他时为 null，前端硬编码展示"其他"）、
 * 文件元数据 originalName/contentLength/contentType（FILE 类型 join file_object 带出）。
 * <p>
 * 不含 description 大字段（列表/搜索 SQL 不 select description，避免污染分页）；
 * 不含审核字段/当前用户态/权限态（访客无需，详情接口另用 {@link ResourcePortalDetailVo}）。
 * <p>
 * 互动计数 likeCount/collectCount/ratingAvg/ratingCount 资源主表刻意不冗余（事实表聚合），
 * 列表 SQL 用 inline 子查询回填（资源量级可接受；超大批量后续再优化）。
 * viewCount/downloadCount 直接读主表冗余列（view_count 由统一浏览历史回写，download_count 仅 FILE 下载 +1）。
 * <p>
 * 铁律：前台 SQL 一律 status='PUBLISHED' AND deleted=0（资源无 level 等级概念，无越级锁态）。
 */
public class ResourcePortalVo {

    private Long resourceId;

    private Long authorId;

    /** 作者昵称（join sys_user on user_id=author_id 带出） */
    private String authorNickname;

    /** 资源类型：FILE 文件 / LINK 链接 */
    private String resourceType;

    private Long resourceCategoryId;

    /** 分类名（join resource_category 带出；-1=其他时为 null，前端硬编码展示"其他"） */
    private String categoryName;

    private String title;

    private String summary;

    /** LINK 类型：外部链接 URL */
    private String linkUrl;

    /** LINK 类型：图标 URL（可空，前端可运行时拼 favicon 兜底） */
    private String linkIcon;

    /** FILE 类型：关联 file_object.object_id */
    private Long fileObjectId;

    /** FILE 类型：原始文件名（join file_object 带出，供卡片/详情展示文件名） */
    private String originalName;

    /** FILE 类型：文件大小字节（join file_object 带出） */
    private Long contentLength;

    /** FILE 类型：MIME 类型（join file_object 带出） */
    private String contentType;

    /** 发布时间（DRAFT 等非公开态不下发前台，前台只见 PUBLISHED） */
    private Date publishTime;

    /** 浏览量（读主表 view_count 冗余列，独立访客数） */
    private Long viewCount;

    /** 下载次数（仅 FILE 下载 +1，主表冗余；与 viewCount 正交） */
    private Long downloadCount;

    /** 点赞数（事实表聚合，inline 子查询回填） */
    private Long likeCount;

    /** 收藏数（事实表聚合，inline 子查询回填） */
    private Long collectCount;

    /** 平均评分 0-5（保留 2 位小数，事实表聚合回填；无评分资源为 0） */
    private Double ratingAvg;

    /** 评分人数（事实表聚合回填） */
    private Long ratingCount;

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceCategoryId() {
        return resourceCategoryId;
    }

    public void setResourceCategoryId(Long resourceCategoryId) {
        this.resourceCategoryId = resourceCategoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getLinkIcon() {
        return linkIcon;
    }

    public void setLinkIcon(String linkIcon) {
        this.linkIcon = linkIcon;
    }

    public Long getFileObjectId() {
        return fileObjectId;
    }

    public void setFileObjectId(Long fileObjectId) {
        this.fileObjectId = fileObjectId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
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

    public Long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
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

    public Double getRatingAvg() {
        return ratingAvg;
    }

    public void setRatingAvg(Double ratingAvg) {
        this.ratingAvg = ratingAvg;
    }

    public Long getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(Long ratingCount) {
        this.ratingCount = ratingCount;
    }

    @Override
    public String toString() {
        return "ResourcePortalVo{" +
                "resourceId=" + resourceId +
                ", resourceType='" + resourceType + '\'' +
                ", resourceCategoryId=" + resourceCategoryId +
                ", title='" + title + '\'' +
                ", publishTime=" + publishTime +
                ", viewCount=" + viewCount +
                ", downloadCount=" + downloadCount +
                ", likeCount=" + likeCount +
                ", collectCount=" + collectCount +
                ", ratingAvg=" + ratingAvg +
                ", ratingCount=" + ratingCount +
                '}';
    }
}