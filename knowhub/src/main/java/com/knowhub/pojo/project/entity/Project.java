package com.knowhub.pojo.project.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 项目主表实体，对应 project 表。
 * 项目管理偏向归档记录（后续可能融入代码版本管理）。公共字段 + level(等级,对标权限) +
 * type(类型,扩展加字典+子表) + status(状态机) + author_id(=LEADER userId)。
 * 主表只存状态机字段(status/review_status/publish_time)，审核员/审核时间/审核意见全在
 * project_review_log 流水表（不冗余主表快照，对齐资源模块）。
 * description 详细介绍走 mediumtext，列表查询不带该列。
 * article_id 关联文章管理模块，非必填，TODO: 文章管理模块开发时关联。
 * authorId 为负责人 userId（= LEADER），与审计列 createBy(username) 互补：
 *   createBy 存账号串便于直显，authorId 用 userId 稳定锁定（username 可改，userId 不变），
 *   前台展示负责人昵称走 join sys_user on user_id=author_id；换负责人时同步更新 author_id。
 * 审计列(createBy/updateBy/createTime/updateTime) 由 BaseEntity 承载；软删 deleted 独立字段。
 */
public class Project extends BaseEntity {

    private Long projectId;

    private String title;

    /** 项目类型：COMPETITION 等（见 ProjectType 枚举，扩展加字典+子表） */
    private String type;

    /** 项目等级 1公开/2内部/3机密（见 ProjectLevel 枚举，对标权限等级 view/download/edit:lN） */
    private Integer level;

    private String summary;

    /** 详细介绍（详情才查，列表不带） */
    private String description;

    /** 关联文章管理ID，非必填。TODO: 文章管理模块开发时关联 */
    private Long articleId;

    /** 负责人userId（= LEADER，对标博客/资源 author_id；换负责人同步更新） */
    private Long authorId;

    /** 状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED（见 ProjectStatus 枚举） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（见 ReviewStatus 枚举，复用） */
    private String reviewStatus;

    private Date publishTime;

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出的展示字段，resultMap 映射，不入库） ----
    /** 负责人昵称（join sys_user on user_id=author_id 带出，非表字段） */
    private String authorNickname;

    /** 当前用户对该项目的项目内权限标志位（详情接口回填，非表字段） */
    private Boolean canView;

    /** 当前用户对该项目的下载权限（详情接口回填，非表字段） */
    private Boolean canDownload;

    /** 当前用户对该项目的编辑权限（详情接口回填，非表字段） */
    private Boolean canEdit;

    /** 当前用户在该项目的成员角色（详情接口回填，非表字段；null=非成员） */
    private String myMemberRole;

    public Project() {
    }

    public Project(Date createTime, Date updateTime, String createBy, String updateBy,
                   Long projectId, String title, String type, Integer level, String summary,
                   String description, Long articleId, Long authorId,
                   String status, String reviewStatus, Date publishTime, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.projectId = projectId;
        this.title = title;
        this.type = type;
        this.level = level;
        this.summary = summary;
        this.description = description;
        this.articleId = articleId;
        this.authorId = authorId;
        this.status = status;
        this.reviewStatus = reviewStatus;
        this.publishTime = publishTime;
        this.deleted = deleted;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
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

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
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

    public Boolean getCanView() {
        return canView;
    }

    public void setCanView(Boolean canView) {
        this.canView = canView;
    }

    public Boolean getCanDownload() {
        return canDownload;
    }

    public void setCanDownload(Boolean canDownload) {
        this.canDownload = canDownload;
    }

    public Boolean getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }

    public String getMyMemberRole() {
        return myMemberRole;
    }

    public void setMyMemberRole(String myMemberRole) {
        this.myMemberRole = myMemberRole;
    }

    @Override
    public String toString() {
        return "Project{" +
                "projectId=" + projectId +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", level=" + level +
                ", authorId=" + authorId +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", publishTime=" + publishTime +
                ", deleted=" + deleted +
                ", createTime=" + getCreateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                '}';
    }
}
