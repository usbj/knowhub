package com.knowhub.pojo.vo;

import java.util.Date;

/**
 * 章节对外 VO，供 Controller 入参/出参。
 * 时间字段一律用 java.util.Date（不要用 String），序列化由全局 jackson.date-format 统一格式化。
 *
 * 章节是"文档站页面"，正文 content 是 mediumtext 大字段：
 * - 列表查询不带 content（避免拖列表），列表 VO 的 content 为 null
 * - 详情查询带 content（整页 Markdown 渲染）
 * 章节不分等级，可见性=文章可见性（articleVisibility join article 带出，供前端展示策略）。
 * authorId 为章节作者 userId（提交者），与 createBy(username) 互补。
 *
 * 权限态（详情接口回填，列表不回填）：
 * - canEdit：当前用户能否编辑该章节（章节作者 OR 文章作者 OR 系统编辑权限够）
 * - canReview：当前用户能否审核该章节（文章作者 OR 系统审核权限，仅 SEMIPUBLIC 待审章节有意义）
 */
public class ChapterVo {

    private Long chapterId;

    private Long articleId;

    private String chapterName;

    private Integer sortOrder;

    private Long authorId;

    /** 章节作者昵称（join sys_user on user_id=author_id 带出） */
    private String authorNickname;

    /** Markdown正文（列表不带，详情才回填） */
    private String content;

    /** 状态：DRAFT/PENDING_AUTHOR_REVIEW/PUBLISHED/REJECTED/REVOKED（字典 chapter_status） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（复用字典 review_status） */
    private String reviewStatus;

    private Date publishTime;

    /** 浏览量（独立访客数，读主表 view_count 冗余列） */
    private Long viewCount;

    // ---- join article 带出的所属文章信息（非章节表字段，供前端展示+权限判定） ----
    /** 所属文章标题（join article 带出） */
    private String articleTitle;

    /** 所属文章可见性（join article 带出；章节提交状态机判定要用） */
    private String articleVisibility;

    /** 所属文章等级（join article 带出；章节编辑权限判定要用） */
    private Integer articleLevel;

    // ---- 当前用户对该章节的权限态（详情接口回填，列表不回填） ----
    private Boolean canEdit;

    private Boolean canReview;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public ChapterVo() {
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getChapterName() {
        return chapterName;
    }

    public void setChapterName(String chapterName) {
        this.chapterName = chapterName;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public String getArticleTitle() {
        return articleTitle;
    }

    public void setArticleTitle(String articleTitle) {
        this.articleTitle = articleTitle;
    }

    public String getArticleVisibility() {
        return articleVisibility;
    }

    public void setArticleVisibility(String articleVisibility) {
        this.articleVisibility = articleVisibility;
    }

    public Integer getArticleLevel() {
        return articleLevel;
    }

    public void setArticleLevel(Integer articleLevel) {
        this.articleLevel = articleLevel;
    }

    public Boolean getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }

    public Boolean getCanReview() {
        return canReview;
    }

    public void setCanReview(Boolean canReview) {
        this.canReview = canReview;
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
        return "ChapterVo{" +
                "chapterId=" + chapterId +
                ", articleId=" + articleId +
                ", chapterName='" + chapterName + '\'' +
                ", sortOrder=" + sortOrder +
                ", authorId=" + authorId +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", articleVisibility='" + articleVisibility + '\'' +
                ", canEdit=" + canEdit +
                ", canReview=" + canReview +
                '}';
    }
}
