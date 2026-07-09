package com.knowhub.pojo.quarry;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 章节列表查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 * 章节列表通常按 articleId 过滤（从文章管理点"章节"进入该文章的章节列表页）。
 *
 * 章节可见性 = 文章可见性（章节不分等级）：
 * - 能看文章就能看其 PUBLISHED 章节
 * - DRAFT/PENDING_AUTHOR_REVIEW/REJECTED 章节仅文章作者 + 章节作者可见
 * 故列表 SQL 过滤透传：
 * - articleId：所属文章（必传，章节列表按文章维度查）
 * - userViewLevel：当前用户查看等级（判定能否看所属文章）
 * - userId：当前用户 userId（章节作者能看自己所有状态的章节 + 文章作者能看该文章所有章节）
 * - articleAuthorId：所属文章作者 userId（service 层回填；= userId 时当前用户是文章作者，可见全部章节）
 * 章节列表查询不带 content（大字段），详情接口单独查。
 */
public class ChapterQuarry {

    /** 所属文章ID（必传，章节按文章维度列表） */
    private Long articleId;

    /** 章节名模糊 */
    private String chapterName;

    /** 章节状态；读者侧默认 PUBLISHED，管理台可传任意 */
    private String status;

    /** 审核状态过滤（管理台看待审用 PENDING） */
    private String reviewStatus;

    /** 章节作者userId过滤（"我提交的章节"场景） */
    private Long authorId;

    /** 创建时间区间起（含）；前端 daterange 传 yyyy-MM-dd，ISO.DATE 显式声明 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 创建时间区间止（含）；前端 daterange 传 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    // ---- 权限过滤透传字段（service 层回填，非前端入参） ----
    /** 当前用户查看等级（0=无系统查看权限，判定能否看所属文章） */
    private Integer userViewLevel;

    /** 当前用户 userId（章节作者 + 文章作者可见性分支用） */
    private Long userId;

    /** 所属文章作者 userId（service 层回填；= userId 时当前用户是文章作者，可见全部章节） */
    private Long articleAuthorId;

    public ChapterQuarry() {
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

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Date getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Date beginTime) {
        this.beginTime = beginTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Integer getUserViewLevel() {
        return userViewLevel;
    }

    public void setUserViewLevel(Integer userViewLevel) {
        this.userViewLevel = userViewLevel;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getArticleAuthorId() {
        return articleAuthorId;
    }

    public void setArticleAuthorId(Long articleAuthorId) {
        this.articleAuthorId = articleAuthorId;
    }

    @Override
    public String toString() {
        return "ChapterQuarry{" +
                "articleId=" + articleId +
                ", chapterName='" + chapterName + '\'' +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", authorId=" + authorId +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                ", userViewLevel=" + userViewLevel +
                ", userId=" + userId +
                ", articleAuthorId=" + articleAuthorId +
                '}';
    }
}
