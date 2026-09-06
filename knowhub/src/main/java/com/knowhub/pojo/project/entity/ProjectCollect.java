package com.knowhub.pojo.project.entity;

import java.util.Date;

/**
 * 项目收藏明细实体，对应 project_collect 表。
 * 主键 (project_id, user_id)；收藏=插入，取消=删除。结构与 BlogCollect/ArticleCollect 对称。
 * 主表 collect_count 列 + ProjectMapper.incrCollectCount 已预留，本期前台互动落地复用。
 */
public class ProjectCollect {

    private Long projectId;

    private Long userId;

    private Date createTime;

    public ProjectCollect() {
    }

    public ProjectCollect(Long projectId, Long userId) {
        this.projectId = projectId;
        this.userId = userId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
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
}