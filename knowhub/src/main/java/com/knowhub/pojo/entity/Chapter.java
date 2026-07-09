package com.knowhub.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 章节表实体，对应 chapter 表（≈博客，正文走主表不分表）。
 * 章节是"文档站页面"，正文 Markdown 整页语义。列表查询不带 content 列避免拖列表。
 * 章节不分等级，可见性=文章可见性（reading 文章能看就能看其 PUBLISHED 章节；
 * DRAFT/PENDING_AUTHOR_REVIEW/REJECTED 仅文章作者 + 章节作者可见）。
 * 主表不冗余审核快照（reviewer/review_time/review_advice 全在 chapter_review_log）。
 * authorId 为章节作者 userId（提交者），与审计列 createBy(username) 互补：
 *   createBy 存账号串便于直显，authorId 用 userId 稳定锁定（username 可改，userId 不变），
 *   前台展示章节作者昵称走 join sys_user on user_id=author_id。
 * status 多一个 PENDING_AUTHOR_REVIEW（仅 SEMIPUBLIC 文章：非作者提交后待文章作者审）。
 * 审计列(createBy/updateBy/createTime/updateTime) 由 BaseEntity 承载；软删 deleted 独立字段。
 */
public class Chapter extends BaseEntity {

    private Long chapterId;

    private Long articleId;

    private String chapterName;

    /** 章节排序（asc，同级按此排序，列表带 sort_order 索引） */
    private Integer sortOrder;

    /** 章节作者userId（提交者；非作者提交且 SEMIPUBLIC 时需文章作者审核） */
    private Long authorId;

    /** Markdown正文（整页文档语义，不分表；列表不带此列避免拖列表） */
    private String content;

    /** 状态：DRAFT/PENDING_AUTHOR_REVIEW/PUBLISHED/REJECTED/REVOKED（见 ChapterStatus 枚举） */
    private String status;

    /** 审核状态：NONE/PENDING/APPROVED/REJECTED（见 ReviewStatus 枚举，复用） */
    private String reviewStatus;

    private Date publishTime;

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出的展示字段，resultMap 映射，不入库） ----
    /** 章节作者昵称（join sys_user on user_id=author_id 带出，非表字段） */
    private String authorNickname;

    /** 所属文章标题（join article on article_id 带出，非表字段，章节列表展示用） */
    private String articleTitle;

    /** 所属文章可见性（join article 带出，非表字段；章节提交状态机判定要用） */
    private String articleVisibility;

    /** 所属文章等级（join article 带出，非表字段；章节编辑权限判定要用） */
    private Integer articleLevel;

    /** 所属文章作者userId（join article 带出，非表字段；章节作者审核比对 author_id 用） */
    private Long articleAuthorId;

    /** 当前用户对该章节的编辑权限（详情接口回填，非表字段） */
    private Boolean canEdit;

    /** 当前用户是否可审核该章节（文章作者或系统审权限，详情接口回填，非表字段） */
    private Boolean canReview;

    public Chapter() {
    }

    public Chapter(Date createTime, Date updateTime, String createBy, String updateBy,
                   Long chapterId, Long articleId, String chapterName, Integer sortOrder,
                   Long authorId, String content,
                   String status, String reviewStatus, Date publishTime, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.chapterId = chapterId;
        this.articleId = articleId;
        this.chapterName = chapterName;
        this.sortOrder = sortOrder;
        this.authorId = authorId;
        this.content = content;
        this.status = status;
        this.reviewStatus = reviewStatus;
        this.publishTime = publishTime;
        this.deleted = deleted;
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

    public Long getArticleAuthorId() {
        return articleAuthorId;
    }

    public void setArticleAuthorId(Long articleAuthorId) {
        this.articleAuthorId = articleAuthorId;
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

    @Override
    public String toString() {
        return "Chapter{" +
                "chapterId=" + chapterId +
                ", articleId=" + articleId +
                ", chapterName='" + chapterName + '\'' +
                ", sortOrder=" + sortOrder +
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
