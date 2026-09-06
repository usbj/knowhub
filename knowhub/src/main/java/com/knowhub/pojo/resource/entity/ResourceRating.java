package com.knowhub.pojo.resource.entity;

import java.util.Date;

/**
 * 资源评分明细实体，对应 resource_rating 表。
 * 主键 (resource_id, user_id)；一人一资源可改分（UNIQUE 支撑 upsert）。
 * 评分均值/计数不冗余主表，走 AVG(score)/COUNT(*) 聚合，写时 Redis 缓存 DEL 保证强一致。
 */
public class ResourceRating {

    private Long ratingId;

    private Long resourceId;

    private Long userId;

    /** 评分 1-5 */
    private Integer score;

    private Date createTime;

    private Date updateTime;

    public ResourceRating() {
    }

    public ResourceRating(Long resourceId, Long userId, Integer score) {
        this.resourceId = resourceId;
        this.userId = userId;
        this.score = score;
    }

    public Long getRatingId() {
        return ratingId;
    }

    public void setRatingId(Long ratingId) {
        this.ratingId = ratingId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "ResourceRating{" +
                "resourceId=" + resourceId +
                ", userId=" + userId +
                ", score=" + score +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
