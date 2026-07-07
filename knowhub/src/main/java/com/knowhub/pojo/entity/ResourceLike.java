package com.knowhub.pojo.entity;

import java.util.Date;

/**
 * 资源点赞明细实体，对应 resource_like 表。
 * 主键 (resource_id, user_id)；点赞=插入，取消=删除。
 * 计数不冗余主表，走 COUNT(*) 聚合（详见 ResourceServiceImpl.fillInteractCounts）。
 */
public class ResourceLike {

    private Long likeId;

    private Long resourceId;

    private Long userId;

    private Date createTime;

    public ResourceLike() {
    }

    public ResourceLike(Long resourceId, Long userId) {
        this.resourceId = resourceId;
        this.userId = userId;
    }

    public Long getLikeId() {
        return likeId;
    }

    public void setLikeId(Long likeId) {
        this.likeId = likeId;
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

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "ResourceLike{" +
                "resourceId=" + resourceId +
                ", userId=" + userId +
                ", createTime=" + createTime +
                '}';
    }
}
