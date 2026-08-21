package com.knowhub.pojo.blog.vo;

import java.util.Date;
import java.util.List;

/**
 * 前台博客轻量 VO（列表/搜索/推荐/相关推荐出参）。
 * <p>
 * 含 authorNickname（join sys_user 回填，补博客既有缺口——博客主表无 author_id 列，只有 create_by(username)，
 * 前台展示昵称走 join sys_user on user_id=??? 但博客无 author_id，故实际回落 create_by）。
 * 不含 content/审核字段/权限态（访客无需，列表类 SQL 不 select content）。
 * <p>
 * 2026-08-18 权限大修搜索范围 +1：前台 SQL 改为 status='PUBLISHED' AND level<=userViewLevel+1，
 * 越级作品（level=userViewLevel+1）进列表带 locked=true 标记、summary 可见，点进详情才锁正文 content。
 * 分级开关关恒 1 → level<=2（L1+L2，L2 带 locked）。
 */
public class BlogPortalVo {

    private Long blogId;

    private Long authorId;

    /** 作者昵称（join sys_user on user_id=author_id 带出；author_id 由 knowhub-blog-review-log.sql 追加，补博客既有缺口） */
    private String authorNickname;

    /** 作者头像 URL（join sys_user.avatar 带出，/file/resolve/{objectId} 形态，无头像为 null，前端 <img> 直引失败回退首字） */
    private String authorAvatar;

    private String title;

    private String summary;

    private String coverUrl;

    /** 博客等级 1公开/2内部/3机密（SQL select b.level 带出，service 据此设 locked 标记） */
    private Integer level;

    /** 越级锁标记：service 层按 vo.level > userViewLevel 置 true，前端据此渲染锁图标（summary 仍可见，正文详情锁） */
    private Boolean locked;

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
