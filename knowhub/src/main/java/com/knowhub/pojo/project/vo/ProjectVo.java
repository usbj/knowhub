package com.knowhub.pojo.project.vo;

import java.util.Date;

/**
 * 项目对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不要用 String），序列化由全局 jackson.date-format
 * 统一格式化为 yyyy-MM-dd HH:mm:ss（见 application.yml），前端用 formatDateTime 展示。
 *
 * 权限分级：
 * - level 项目等级（1/2/3），对标系统权限 view/download/edit:lN
 * - canView/canDownload/canEdit/myMemberRole：详情接口回填当前用户对该项目的权限态
 *   （系统权限等级够 OR 项目内成员标志位 OR LEADER 全权），列表不回填
 * authorId 为负责人 userId（= LEADER），与 createBy(username) 互补，前台展示昵称走 join sys_user。
 * 审核快照（审核员/审核时间/审核意见）不冗余主表，走 project_review_log 流水表。
 * articleId 关联文章管理模块（待开发，非必填）。
 */
public class ProjectVo {

    private Long projectId;

    private String title;

    /** 项目类型：COMPETITION 等（字典 project_type） */
    private String type;

    /** 项目等级 1公开/2内部/3机密（字典 project_level，对标权限等级） */
    private Integer level;

    private String summary;

    private String description;

    /** 关联文章管理ID，非必填。TODO: 文章管理模块开发时关联 */
    private Long articleId;

    private Long authorId;

    /** 负责人昵称（join sys_user on user_id=author_id 带出） */
    private String authorNickname;

    /** 状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED（字典 project_status） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（复用字典 review_status） */
    private String reviewStatus;

    private Date publishTime;

    // ---- 当前用户对该项目的权限态（详情接口回填，列表不回填） ----
    private Boolean canView;

    private Boolean canDownload;

    private Boolean canEdit;

    /** 当前用户在该项目的成员角色（null=非成员；LEADER/MENTOR/MEMBER） */
    private String myMemberRole;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public ProjectVo() {
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

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
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

    @Override
    public String toString() {
        return "ProjectVo{" +
                "projectId=" + projectId +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", level=" + level +
                ", authorId=" + authorId +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", canView=" + canView +
                ", canDownload=" + canDownload +
                ", canEdit=" + canEdit +
                ", myMemberRole='" + myMemberRole + '\'' +
                '}';
    }
}
