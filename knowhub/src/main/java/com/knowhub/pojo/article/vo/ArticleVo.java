package com.knowhub.pojo.article.vo;

import java.util.Date;

/**
 * 文章对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不要用 String），序列化由全局 jackson.date-format
 * 统一格式化为 yyyy-MM-dd HH:mm:ss（见 application.yml），前端用 formatDateTime 展示。
 *
 * 权限分级：
 * - level 文章等级（1/2/3），对标系统权限 view/edit:lN
 * - canView/canEdit/isAuthor：详情接口回填当前用户对该文章的权限态
 *   （系统权限等级够 OR 作者全权），列表不回填
 * visibility（PRIVATE/SEMIPUBLIC/PUBLIC）是文章内部可见性，决定章节提交审不审，与等级正交。
 * authorId 为作者 userId（单一所有者），与 createBy(username) 互补，前台展示昵称走 join sys_user。
 * 审核快照（审核员/审核时间/审核意见）不冗余主表，走 article_review_log 流水表。
 */
public class ArticleVo {

    private Long articleId;

    private String title;

    /** 前言/编者按（整书导言，mediumtext，列表可预览） */
    private String summary;

    /** 文章等级 1公开/2内部/3机密（字典 article_level，对标权限等级） */
    private Integer level;

    /** 内部可见性 PRIVATE未公开/SEMIPUBLIC半公开/PUBLIC全公开（字典 article_visibility，决定章节提交审不审） */
    private String visibility;

    private Long authorId;

    /** 作者昵称（join sys_user on user_id=author_id 带出） */
    private String authorNickname;

    /** 封面图 RustFS 对象key（对接 file_object business_type=ARTICLE_COVER） */
    private String coverObjectKey;

    /** 状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED（字典 article_status，审核状态机同博客） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（复用字典 review_status） */
    private String reviewStatus;

    private Date publishTime;

    /** 浏览量（独立访客数，读主表 view_count 冗余列） */
    private Long viewCount;

    /** 点赞量（读主表 like_count 冗余列，以 article_like 为准） */
    private Long likeCount;

    /** 收藏量（读主表 collect_count 冗余列，以 article_collect 为准） */
    private Long collectCount;

    /** 标签ID列表（详情/编辑回填，照博客 BlogVo.tagIds 范式） */
    private java.util.List<Long> tagIds;

    /** 标签名列表（详情回填，按 tagIds 取启用标签名） */
    private java.util.List<String> tagNames;

    // ---- 当前用户对该文章的权限态（详情接口回填，列表不回填） ----
    private Boolean canView;

    private Boolean canEdit;

    /** 当前用户是否为该文章作者（作者全权不看等级/不看 visibility） */
    private Boolean isAuthor;

    /**
     * 当前用户能否像作者那样自由管理文章章节（排序/改文章元信息/发布/撤回/删除）。
     * = 文章作者 OR 系统编辑级（edit:lN≥level），不含被作者批准的贡献者。
     * 与 canEdit 区分：canEdit 含贡献者分支（贡献者可提交新章节/编辑自己章节），canManageChapters 不含——
     * 贡献者不可改章节排序、不可发布/撤回/删除文章、不可删任意章节。详情接口回填，列表不回填。
     */
    private Boolean canManageChapters;

    /**
     * 「我的作品」列表当前用户对该文章的角色标识：AUTHOR=我是作者；CONTRIBUTOR=我被批准为贡献者；null=无归属。
     * 仅 quarryArticle（/authoring/article/my 列表）回填，驱动前端行上「贡献者」标识。作者默认不显标识（默认即作者）。
     */
    private String myRole;

    /** 评论区开关 1开/0关（见 comment 模块） */
    private Integer commentEnabled;

    /** 评论精选开关 0=新评论直接可见 / 1=新评论仅发表人+作者可见，作者同意展示后他人可见 */
    private Integer commentCurated;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public ArticleVo() {
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

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
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

    public java.util.List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(java.util.List<Long> tagIds) {
        this.tagIds = tagIds;
    }

    public java.util.List<String> getTagNames() {
        return tagNames;
    }

    public void setTagNames(java.util.List<String> tagNames) {
        this.tagNames = tagNames;
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

    public Boolean getCanManageChapters() {
        return canManageChapters;
    }

    public void setCanManageChapters(Boolean canManageChapters) {
        this.canManageChapters = canManageChapters;
    }

    public String getMyRole() {
        return myRole;
    }

    public void setMyRole(String myRole) {
        this.myRole = myRole;
    }

    public Integer getCommentEnabled() {
        return commentEnabled;
    }

    public void setCommentEnabled(Integer commentEnabled) {
        this.commentEnabled = commentEnabled;
    }

    public Integer getCommentCurated() {
        return commentCurated;
    }

    public void setCommentCurated(Integer commentCurated) {
        this.commentCurated = commentCurated;
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
        return "ArticleVo{" +
                "articleId=" + articleId +
                ", title='" + title + '\'' +
                ", level=" + level +
                ", visibility='" + visibility + '\'' +
                ", authorId=" + authorId +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", canView=" + canView +
                ", canEdit=" + canEdit +
                ", isAuthor=" + isAuthor +
                ", canManageChapters=" + canManageChapters +
                ", myRole='" + myRole + '\'' +
                '}';
    }
}
