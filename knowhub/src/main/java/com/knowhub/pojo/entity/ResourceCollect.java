package com.knowhub.pojo.entity;

import java.util.Date;

/**
 * 资源收藏明细实体，对应 resource_collect 表。
 * 主键 (resource_id, user_id)；收藏=插入，取消=删除。结构与 ResourceLike 对称。
 * 计数不冗余主表，走 COUNT(*) 聚合。
 */
public class ResourceCollect {

    private Long collectId;

    private Long resourceId;

    private Long userId;

    private Date createTime;

    public ResourceCollect() {
    }

    public ResourceCollect(Long resourceId, Long userId) {
        this.resourceId = resourceId;
        this.userId = userId;
    }

    public Long getCollectId() {
        return collectId;
    }

    public void setCollectId(Long collectId) {
        this.collectId = collectId;
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
        return "ResourceCollect{" +
                "resourceId=" + resourceId +
                ", userId=" + userId +
                ", createTime=" + createTime +
                '}';
    }
}
