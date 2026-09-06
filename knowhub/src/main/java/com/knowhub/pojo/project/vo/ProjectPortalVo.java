package com.knowhub.pojo.project.vo;

import java.util.Date;

/**
 * 前台项目轻量 VO（门户列表/搜索/推荐/相关推荐出参）。
 * <p>
 * 照博客 {@code BlogPortalVo} 精简于 {@link ProjectVo}：只含访客可见的展示字段，
 * 不含 description/审核字段/权限态（访客无需，列表 SQL 不 select description 大字段）。
 * 含计数字段（viewCount/likeCount/collectCount/downloadCount，2026-08-03 补建主表冗余列），
 * 用于卡片展示下载量 + 推荐打分。
 * <p>
 * 2026-08-18 权限大修搜索范围 +1：前台 SQL 改为 status='PUBLISHED' AND deleted=0 AND level<=userViewLevel+1，
 * 越级作品（level=userViewLevel+1）进列表带 locked=true 标记、summary 可见，点进详情只锁下载（description 可见）。
 * 分级开关关恒 1 → level<=2（L1+L2，L2 带 locked）。
 *
 * @author knowhub
 */
public class ProjectPortalVo {

    private Long projectId;

    private String title;

    /** 项目类型：COMPETITION 等（字典 project_type） */
    private String type;

    /** 项目等级 1公开/2内部/3机密（字典 project_level，对标权限等级） */
    private Integer level;

    private String summary;

    /** 负责人 userId（用于详情判断是否作者本人，留待 related 同作者召回等） */
    private Long authorId;

    /** 负责人昵称（join sys_user on user_id=author_id 带出） */
    private String authorNickname;

    /** 负责人头像 URL（join sys_user.avatar 带出，无头像为 null，前端 <img> 直引失败回退首字） */
    private String authorAvatar;

    /** 越级锁标记：service 层按 vo.level > userViewLevel 置 true，前端据此渲染锁图标（summary 仍可见，只锁下载） */
    private Boolean locked;

    private Date publishTime;

    private Long viewCount;

    private Long likeCount;

    private Long collectCount;

    private Long downloadCount;

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

    public Long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }
}