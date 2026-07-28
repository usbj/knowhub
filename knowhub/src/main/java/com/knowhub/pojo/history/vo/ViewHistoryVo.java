package com.knowhub.pojo.history.vo;

import java.util.Date;

/**
 * 浏览历史列表项 VO（当前用户历史分页出参）。
 * join blog/article/resource 各主表带出 title/coverUrl/authorName 供前端卡片展示。
 * 章节类型取所属 article 标题作 title（章节无独立标题列面向历史展示）。
 */
public class ViewHistoryVo {

    private Long viewId;

    /** 业务类型 BLOG/ARTICLE/CHAPTER/RESOURCE */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 内容标题（blog.title / article.title；章节取所属 article 标题） */
    private String title;

    /** 封面图 RustFS 对象key（blog 无独立封面列暂 null；article.cover_object_key） */
    private String coverObjectKey;

    /** 作者昵称（join sys_user） */
    private String authorName;

    /** 首次浏览时间 */
    private Date viewTime;

    /** 最近浏览时间 */
    private Date lastViewTime;

    /** 累计浏览次数 */
    private Integer viewCount;

    public Long getViewId() {
        return viewId;
    }

    public void setViewId(Long viewId) {
        this.viewId = viewId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCoverObjectKey() {
        return coverObjectKey;
    }

    public void setCoverObjectKey(String coverObjectKey) {
        this.coverObjectKey = coverObjectKey;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public Date getViewTime() {
        return viewTime;
    }

    public void setViewTime(Date viewTime) {
        this.viewTime = viewTime;
    }

    public Date getLastViewTime() {
        return lastViewTime;
    }

    public void setLastViewTime(Date lastViewTime) {
        this.lastViewTime = lastViewTime;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }
}
