package com.knowhub.pojo.comment.quarry;

/**
 * 评论列表分页查询条件，作为 Mapper parameterType。
 * <p>
 * bizType + bizId 必填（按作品查评论）；currentUserId / workAuthorId 由 service 透传给 SQL，
 * 用于权限谓词「NONE/APPROVED 或 本人 或 该作品作者」（见 CommentMapper.listComments 谓词）。
 * pageNum/pageSize 由 PageUtil 从请求读取（与 ViewHistoryQuarry 范式一致，此处不声明）。
 * order：new=按创建时间倒序（默认），like=按点赞数倒序——按 new 为主，like 为次。
 */
public class CommentListQuarry {

    /** 业务类型 BLOG/ARTICLE/PROJECT/RESOURCE */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 当前登录用户ID（service 层透传，未登录为 null） */
    private Long currentUserId;

    /** 该作品作者ID（service 层按 bizType+bizId 解析主表带出，供 SQL 判作者身份 inline 审核） */
    private Long workAuthorId;

    /** 排序：new=按时间倒序（默认），like=按点赞数倒序 */
    private String order;

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

    public Long getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(Long currentUserId) {
        this.currentUserId = currentUserId;
    }

    public Long getWorkAuthorId() {
        return workAuthorId;
    }

    public void setWorkAuthorId(Long workAuthorId) {
        this.workAuthorId = workAuthorId;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }
}