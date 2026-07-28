package com.knowhub.pojo.entity;

import java.util.Date;

/**
 * 统一浏览明细事实表实体，对应 user_view_history 表（三模块共用，防刷核心）。
 * <p>
 * UNIQUE(user_id, biz_type, biz_id) 去重防刷：同用户同内容只一行，{@code viewCount} 累计访问次数。
 * 主表 view_count 冗余列只在首次 INSERT 这行时 +1（记独立访客数），后续累加只动本表 viewCount，刷不动主表。
 * biz_type 取值见 {@link com.knowhub.enums.ViewBizType}（BLOG/ARTICLE/CHAPTER/RESOURCE），biz_id 统一 bigint。
 */
public class UserViewHistory {

    private Long viewId;

    private Long userId;

    /** 业务类型 BLOG/ARTICLE/CHAPTER/RESOURCE（见 ViewBizType 枚举） */
    private String bizType;

    /** 业务ID（各模块主键，bigint） */
    private Long bizId;

    /** 首次浏览时间 */
    private Date viewTime;

    /** 最近浏览时间 */
    private Date lastViewTime;

    /** 同一内容累计浏览次数 */
    private Integer viewCount;

    public UserViewHistory() {
    }

    public Long getViewId() {
        return viewId;
    }

    public void setViewId(Long viewId) {
        this.viewId = viewId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    @Override
    public String toString() {
        return "UserViewHistory{" +
                "viewId=" + viewId +
                ", userId=" + userId +
                ", bizType='" + bizType + '\'' +
                ", bizId=" + bizId +
                ", viewCount=" + viewCount +
                '}';
    }
}
