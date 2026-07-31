package com.knowhub.pojo.article.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 文章主表实体，对应 article 表。
 * 文章 = 章节集合（参考 Vue / Element-Plus 官方文档站：一篇文章是一本"文档书"，
 * 章节是其中的"页面"）。公共字段 + level(等级,对标权限) + visibility(内部可见性三档) +
 * author_id(单一作者) + 审核状态机(status/review_status/publish_time)。
 * 主表不存正文（正文全在 chapter），不冗余审核快照（reviewer/review_time/review_advice
 * 全在 article_review_log 流水表，比博客主表更干净，对齐项目范式）。
 * summary 走 mediumtext（前言/编者按，列表可预览）；封面走 file_object(ARTICLE_COVER)。
 * 不加 project_id（单向关联：project.article_id → article，文章侧不反查）。
 * authorId 为作者 userId（单一所有者，类比项目 LEADER），与审计列 createBy(username) 互补：
 *   createBy 存账号串便于直显，authorId 用 userId 稳定锁定（username 可改，userId 不变），
 *   前台展示作者昵称走 join sys_user on user_id=author_id。
 * 审计列(createBy/updateBy/createTime/updateTime) 由 BaseEntity 承载；软删 deleted 独立字段。
 */
public class Article extends BaseEntity {

    private Long articleId;

    private String title;

    /** 前言/编者按（整书导言，列表可预览，走 mediumtext） */
    private String summary;

    /** 文章等级 1公开/2内部/3机密（见 ArticleLevel 枚举，对标权限等级 view/edit:lN） */
    private Integer level;

    /** 内部可见性 PRIVATE未公开/SEMIPUBLIC半公开/PUBLIC全公开（见 ArticleVisibility 枚举，决定章节提交审不审） */
    private String visibility;

    /** 作者userId（单一所有者，类比项目 LEADER；作者对自己的文章全权，不看等级/不看 visibility） */
    private Long authorId;

    /** 封面图 RustFS 对象key（对接 file_object business_type=ARTICLE_COVER biz_ref_id=article_id） */
    private String coverObjectKey;

    /** 状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED（见 ArticleStatus 枚举，审核状态机同博客） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（见 ReviewStatus 枚举，复用） */
    private String reviewStatus;

    private Date publishTime;

    /** 浏览量（独立访客数，user_view_history 首次 INSERT +1，冗余列读快） */
    private Long viewCount;

    /** 点赞量（冗余列，以 article_like 为准，前台 toggle 接口事务内同步递增/递减） */
    private Long likeCount;

    /** 收藏量（冗余列，以 article_collect 为准，前台 toggle 接口事务内同步递增/递减） */
    private Long collectCount;

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出的展示字段，resultMap 映射，不入库） ----
    /** 作者昵称（join sys_user on user_id=author_id 带出，非表字段） */
    private String authorNickname;

    /** 当前用户对该文章的查看权限（详情接口回填，非表字段） */
    private Boolean canView;

    /** 当前用户对该文章的编辑权限（详情接口回填，非表字段） */
    private Boolean canEdit;

    /** 当前用户是否为该文章作者（详情接口回填，非表字段；作者全权不看等级/不看 visibility） */
    private Boolean isAuthor;

    public Article() {
    }

    public Article(Date createTime, Date updateTime, String createBy, String updateBy,
                   Long articleId, String title, String summary, Integer level, String visibility,
                   Long authorId, String coverObjectKey,
                   String status, String reviewStatus, Date publishTime, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.articleId = articleId;
        this.title = title;
        this.summary = summary;
        this.level = level;
        this.visibility = visibility;
        this.authorId = authorId;
        this.coverObjectKey = coverObjectKey;
        this.status = status;
        this.reviewStatus = reviewStatus;
        this.publishTime = publishTime;
        this.deleted = deleted;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
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

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getCoverObjectKey() {
        return coverObjectKey;
    }

    public void setCoverObjectKey(String coverObjectKey) {
        this.coverObjectKey = coverObjectKey;
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

    public Boolean getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }

    public Boolean getIsAuthor() {
        return isAuthor;
    }

    public void setIsAuthor(Boolean isAuthor) {
        this.isAuthor = isAuthor;
    }

    @Override
    public String toString() {
        return "Article{" +
                "articleId=" + articleId +
                ", title='" + title + '\'' +
                ", level=" + level +
                ", visibility='" + visibility + '\'' +
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
