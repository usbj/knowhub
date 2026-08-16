package com.knowhub.pojo.blog.quarry;

import java.util.Date;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 博客文章列表查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 * 支持按标题模糊、全文关键词、等级、标签、状态、审核状态、创建人、时间区间过滤。
 *
 * 权限过滤透传字段（service 层从 BlogPermissionResolver 算出后回填，Mapper SQL 用）：
 * - userViewLevel：当前用户查看等级（0=无系统查看权限）
 * - userId：当前用户 userId（用于"作者能看自己的博客"分支：author_id = userId）
 * 前台"我的博客"强制收紧字段（前台 controller 注入，admin 后台不注入）：
 * - authorId：前台 myList 注入当前用户 userId → SQL 叠加 author_id=#{authorId} 强制AND，
 *   把"level<=userViewLevel OR author_id=userId"OR 分支收紧到本人创建，避免召回他人博客。
 *   后台 admin 管理台不注入此字段，<if>不命中，原"有权看"召回口径不受影响（与 Article/Project 同位范式）。
 * 博客无成员表（轻量权限模型，对齐文章模块），列表 SQL 过滤：
 *   level <= userViewLevel OR author_id = userId（作者始终能看自己的博客，不看等级）
 * 无系统查看权限者 userViewLevel=0，level<=0 永假，只走 author_id=userId 分支 → 只看自己写的。
 */
public class BlogQuarry {

    /** 标题模糊（不走 fulltext，作为简单过滤；正文检索用 keyword） */
    private String title;

    /** 全文检索关键词，命中 title/content 的 FULLTEXT(ngram) */
    private String keyword;

    /** 博客等级 1/2/3（见 BlogLevel 枚举，前端按等级筛选用） */
    private Integer level;

    /** 标签 id 列表，走 blog_tag join + IN 精确过滤 */
    private List<Long> tagIds;

    /** 文章状态；读者侧默认 PUBLISHED，管理台可传任意 */
    private String status;

    /** 审核状态过滤（管理台看待审用 PENDING） */
    private String reviewStatus;

    /** 创建人(作者)过滤 */
    private String createBy;

    /** 创建时间区间起（含）；前端 daterange 传 yyyy-MM-dd，ISO.DATE 显式声明避免依赖 Spring 默认转换器 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date beginTime;

    /** 创建时间区间止（含）；前端 daterange 传 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private Date endTime;

    // ---- "我的博客"前台列表强制收紧字段（BlogAuthoringController.myList 注入当前用户 userId） ----
    /**
     * 作者 userId 过滤（"我的博客"场景，由前台 controller 注入当前用户 userId）。
     * Mapper SQL 在此字段非空时叠加 author_id = #{authorId} 强制 AND，
     * 把"level<=userViewLevel OR author_id=userId"的 OR 分支收紧到本人创建，
     * 与 Article/Project 同位 myList 范式一致——避免登录用户在 /authoring/blog/list 召回他人博客。
     * 后台 admin 不注入此字段，<if> 不命中，原"有权看"召回口径不受影响。
     */
    private Long authorId;

    // ---- 权限过滤透传字段（service 层回填，非前端入参） ----
    /** 当前用户查看等级（0=无系统查看权限） */
    private Integer userViewLevel;

    /** 当前用户 userId（作者能看自己的博客分支用） */
    private Long userId;

    public BlogQuarry() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
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

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    @Override
    public String toString() {
        return "BlogQuarry{" +
                "title='" + title + '\'' +
                ", keyword='" + keyword + '\'' +
                ", level=" + level +
                ", tagIds=" + tagIds +
                ", status='" + status + '\'' +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", createBy='" + createBy + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                ", authorId=" + authorId +
                ", userViewLevel=" + userViewLevel +
                ", userId=" + userId +
                '}';
    }
}