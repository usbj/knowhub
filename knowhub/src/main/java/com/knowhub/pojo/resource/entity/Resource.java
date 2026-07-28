package com.knowhub.pojo.resource.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 资源主表实体，对应 resource 表。
 * 资源分两类：FILE 文件（走文件存储模块上传，关联 file_object_id）/ LINK 链接（存 link_url）。
 * 主表只存状态机字段(status/review_status/publish_time)，审核员/审核时间/审核意见全在
 * resource_review_log 流水表（不冗余主表快照，比博客主表更干净）。
 * 互动计数(点赞/收藏/评分)不冗余主表，走事实表聚合；仅 download_count 冗余主表(仅FILE下载+1)。
 * authorId 为作者用户ID(userId)，与审计列 createBy(username) 互补：
 *   createBy 存账号串便于直接展示，authorId 用 userId 稳定锁定（username 可改，userId 不变），
 *   前台展示作者昵称走 join sys_user on user_id=author_id，username 变动不影响。
 * resourceCategoryId 写全名（不写 categoryId，防歧义），-1=其他（前端硬编码约定）。
 * 审计列(createBy/updateBy/createTime/updateTime) 由 BaseEntity 承载；软删 deleted 独立字段。
 */
public class Resource extends BaseEntity {

    private Long resourceId;

    private Long authorId;

    /** 资源类型：FILE 文件 / LINK 链接（见 ResourceType 枚举） */
    private String resourceType;

    /** 分类 id，-1=其他（前端硬编码约定，不查分类表） */
    private Long resourceCategoryId;

    private String title;

    private String summary;

    /** 详细说明（支持 Markdown） */
    private String description;

    /** FILE 类型：关联 file_object.object_id（RESOURCE_FILE 业务） */
    private Long fileObjectId;

    /** LINK 类型：外部链接 URL */
    private String linkUrl;

    /** LINK 类型：图标 URL（首版运行时拼 favicon，可空） */
    private String linkIcon;

    /** 状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED（见 ResourceStatus 枚举） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（见 ReviewStatus 枚举，复用） */
    private String reviewStatus;

    private Date publishTime;

    /** 下载次数（仅 FILE 下载 +1，LINK 点击不计） */
    private Long downloadCount;

    /** 浏览量（独立访客数，user_view_history 首次 INSERT +1，冗余列读快；与 download_count 正交） */
    private Long viewCount;

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出的展示字段，resultMap 映射，不入库） ----
    /** 作者昵称（join sys_user on user_id=author_id 带出，非表字段） */
    private String authorNickname;

    /** 分类名（join resource_category 带出；-1=其他时为 null，非表字段） */
    private String categoryName;

    /** FILE 类型：原始文件名（join file_object 带出，非表字段） */
    private String originalName;

    /** FILE 类型：文件大小字节（join file_object 带出，非表字段） */
    private Long contentLength;

    /** FILE 类型：MIME 类型（join file_object 带出，非表字段） */
    private String contentType;

    public Resource() {
    }

    public Resource(Date createTime, Date updateTime, String createBy, String updateBy,
                    Long resourceId, Long authorId, String resourceType, Long resourceCategoryId,
                    String title, String summary, String description,
                    Long fileObjectId, String linkUrl, String linkIcon,
                    String status, String reviewStatus, Date publishTime, Long downloadCount, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.resourceId = resourceId;
        this.authorId = authorId;
        this.resourceType = resourceType;
        this.resourceCategoryId = resourceCategoryId;
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.fileObjectId = fileObjectId;
        this.linkUrl = linkUrl;
        this.linkIcon = linkIcon;
        this.status = status;
        this.reviewStatus = reviewStatus;
        this.publishTime = publishTime;
        this.downloadCount = downloadCount;
        this.deleted = deleted;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
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

    @Override
    public String toString() {
        return "Resource{" +
                "resourceId=" + resourceId +
                ", authorId=" + authorId +
                ", resourceType='" + resourceType + '\'' +
                ", resourceCategoryId=" + resourceCategoryId +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", publishTime=" + publishTime +
                ", downloadCount=" + downloadCount +
                ", deleted=" + deleted +
                ", createTime=" + getCreateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                '}';
    }
}
