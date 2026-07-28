package com.knowhub.pojo.resource.vo;

import java.util.Date;

/**
 * 资源对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不要用 String），序列化由全局 jackson.date-format
 * 统一格式化为 yyyy-MM-dd HH:mm:ss（见 application.yml），前端用 formatDateTime 展示。
 *
 * 互动计数（点赞/收藏/评分）不冗余主表，由 Service 层聚合事实表回填：
 * - likeCount / collectCount / ratingAvg / ratingCount：列表批量聚合或详情单条聚合
 * - hasLiked / hasCollected / myScore：详情接口回填当前用户态
 * authorId 为作者用户ID(userId)，与 createBy(username) 互补，前台展示昵称走 join sys_user。
 * categoryName 由 join resource_category 带出；-1=其他时 categoryName 置空（前端硬编码展示"其他"）。
 * downloadUrl：FILE 类型详情时回填（中转模式 /file/proxy/{objectId}；直链模式预签名），
 *              LINK 类型 downloadUrl 置空，前端用 linkUrl 外链打开。
 */
public class ResourceVo {

    private Long resourceId;

    private Long authorId;

    /** 资源类型：FILE / LINK */
    private String resourceType;

    private Long resourceCategoryId;

    /** 分类名（join 带出；-1=其他时为 null，前端硬编码展示"其他"） */
    private String categoryName;

    private String title;

    private String summary;

    private String description;

    /** FILE 类型：关联 file_object.object_id */
    private Long fileObjectId;

    /** FILE 类型：原始文件名（join file_object 带出，供详情展示下载文件名） */
    private String originalName;

    /** FILE 类型：文件大小字节（join file_object 带出） */
    private Long contentLength;

    /** FILE 类型：MIME 类型（join file_object 带出） */
    private String contentType;

    /** LINK 类型：外部链接 URL */
    private String linkUrl;

    /** LINK 类型：图标 URL（可空，前端可运行时拼 favicon 兜底） */
    private String linkIcon;

    private String status;

    private String reviewStatus;

    private Date publishTime;

    /** 下载次数（仅 FILE 下载 +1，主表冗余） */
    private Long downloadCount;

    /** 浏览量（独立访客数，读主表 view_count 冗余列；与 download_count 正交） */
    private Long viewCount;

    // ---- 互动计数（事实表聚合回填，不冗余主表） ----
    private Long likeCount;

    private Long collectCount;

    /** 平均评分 0-5（保留 2 位小数，聚合回填） */
    private Double ratingAvg;

    private Long ratingCount;

    // ---- 当前用户态（详情接口回填） ----
    private Boolean hasLiked;

    private Boolean hasCollected;

    /** 当前用户评分（无则 0 或 null） */
    private Integer myScore;

    // ---- FILE 下载链接（详情接口按访问模式回填） ----
    private String downloadUrl;

    private String createBy;

    /** 作者昵称（join sys_user on user_id=author_id 带出） */
    private String authorNickname;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public ResourceVo() {
    }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Date getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Date publishTime) {
        this.publishTime = publishTime;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
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

    public Integer getMyScore() {
        return myScore;
    }

    public void setMyScore(Integer myScore) {
        this.myScore = myScore;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
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

    @Override
    public String toString() {
        return "ResourceVo{" +
                "resourceId=" + resourceId +
                ", resourceType='" + resourceType + '\'' +
                ", resourceCategoryId=" + resourceCategoryId +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", downloadCount=" + downloadCount +
                ", likeCount=" + likeCount +
                ", collectCount=" + collectCount +
                ", ratingAvg=" + ratingAvg +
                ", ratingCount=" + ratingCount +
                ", hasLiked=" + hasLiked +
                ", hasCollected=" + hasCollected +
                ", myScore=" + myScore +
                '}';
    }
}
