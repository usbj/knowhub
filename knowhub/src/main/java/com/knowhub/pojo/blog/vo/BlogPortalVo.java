package com.knowhub.pojo.blog.vo;

import java.util.Date;
import java.util.List;

/**
 * 前台博客轻量 VO（列表/搜索/推荐/相关推荐出参）。
 * <p>
 * 含 authorNickname（join sys_user 回填，补博客既有缺口——博客主表无 author_id 列，只有 create_by(username)，
 * 前台展示昵称走 join sys_user on user_id=??? 但博客无 author_id，故实际回落 create_by）。
 * 不含 content/审核字段/权限态（访客无需，列表类 SQL 不 select content）。
 * 铁律：前台 SQL 一律 status=PUBLISHED AND level=1（分级开关开则 level<=userViewLevel）。
 */
public class BlogPortalVo {

    private Long blogId;

    private Long authorId;

    /** 作者昵称（join sys_user on user_id=author_id 带出；author_id 由 knowhub-blog-review-log.sql 追加，补博客既有缺口） */
    private String authorNickname;

    private String title;

    private String summary;

    private String coverUrl;

    private Date publishTime;

    private Long viewCount;

    private Long likeCount;

    private Long collectCount;

    private List<Long> tagIds;

    private List<String> tagNames;

    public Long getBlogId() {
        return blogId;
    }

    public void setBlogId(Long blogId) {
        this.blogId = blogId;
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
}
