package com.knowhub.pojo.comment.quarry;

/**
 * 回复列表分页查询条件（取某顶级评论下的回复，两层嵌套第三层）。
 * <p>
 * parentId 必填（顶级评论 comment_id）；currentUserId / workAuthorId 由 service 透传给 SQL，
 * 权限谓词与 {@link CommentListQuarry} 一致（PENDING/REJECTED 仅发表人+作品作者可见）。
 * 回复列表默认按创建时间倒序（楼层顺序）。
 */
public class ReplyListQuarry {

    /** 顶级评论 comment_id */
    private Long parentId;

    /** 当前登录用户ID（service 透传，未登录为 null） */
    private Long currentUserId;

    /** 该作品作者ID（service 按 parent 的 biz_type+biz_id 解析作品带出） */
    private Long workAuthorId;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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
}