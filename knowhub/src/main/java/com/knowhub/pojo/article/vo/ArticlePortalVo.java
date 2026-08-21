package com.knowhub.pojo.article.vo;

import java.util.Date;
import java.util.List;

/**
 * 前台文章轻量 VO（列表/搜索/推荐/相关推荐出参）。照搬 BlogPortalVo 范式。
 * <p>
 * 文章 = 章节集合（文档站结构），正文不走 article 表，故列表不带正文；章节大纲与正文走详情接口。
 * matchedChapters 仅搜索接口填充（章节正文命中时标出对应章节，便于结果点击跳章节阅读页）；
 * 其余列表/推荐/相关接口该字段为空。
 * <p>
 * 2026-08-18 权限大修搜索范围 +1：前台 SQL 改为 deleted=0 AND status='PUBLISHED' AND level<=userViewLevel+1，
 * 越级作品（level=userViewLevel+1）进列表带 locked=true 标记、摘要可见，点进详情才锁章节大纲/正文。
 * 分级开关关则 userViewLevel 恒 1 → level<=2（L1+L2，L2 带 locked）。
 * <p>
 * coverUrl 走 file_object(ARTICLE_COVER) 的 resolve 链路：SQL 取 object_id 后 concat 成 /file/resolve/{objectId}，
 * 前端 img src 直用（resolve 接口按访问模式 302 跳转，对齐 FileService 落库稳定引用语义）。
 */
public class ArticlePortalVo {

    private Long articleId;

    private Long authorId;

    /** 作者昵称（join sys_user on user_id=author_id 带出） */
    private String authorNickname;

    /** 作者头像 URL（join sys_user.avatar 带出，无头像为 null，前端 <img> 直引失败回退首字） */
    private String authorAvatar;

    private String title;

    /** 前言/编者按（mediumtext，列表可预览，越级时仍可见——锁的是章节大纲/正文） */
    private String summary;

    /** 封面 URL（/file/resolve/{objectId} 形态，无封面为 null） */
    private String coverUrl;

    /** 文章等级 1公开/2内部/3机密（meta 带出，前台可据此提示） */
    private Integer level;

    /** 越级锁标记：service 层按 vo.level > userViewLevel 置 true，前端据此渲染锁图标（摘要仍可见） */
    private Boolean locked;

    private Date publishTime;

    private Long viewCount;

    private Long likeCount;

    private Long collectCount;

    /** 章节数量（join chapter 聚合或子查询带出，文档站章节数展示用） */
    private Integer chapterCount;

    private List<Long> tagIds;

    private List<String> tagNames;

    /** 搜索命中的章节列表（仅 search 接口填充，标出对应章节便于跳转；其余接口为空） */
    private List<MatchedChapterVo> matchedChapters;

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

    public String getAuthorAvatar() {
        return authorAvatar;
    }

    public void setAuthorAvatar(String authorAvatar) {
        this.authorAvatar = authorAvatar;
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

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
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

    public Integer getChapterCount() {
        return chapterCount;
    }

    public void setChapterCount(Integer chapterCount) {
        this.chapterCount = chapterCount;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }

    public List<String> getTagNames() {
        return tagNames;
    }

    public void setTagNames(List<String> tagNames) {
        this.tagNames = tagNames;
    }

    public List<MatchedChapterVo> getMatchedChapters() {
        return matchedChapters;
    }

    public void setMatchedChapters(List<MatchedChapterVo> matchedChapters) {
        this.matchedChapters = matchedChapters;
    }
}