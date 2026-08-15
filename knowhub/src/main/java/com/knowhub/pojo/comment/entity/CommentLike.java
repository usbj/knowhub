package com.knowhub.pojo.comment.entity;

import java.util.Date;

/**
 * 评论点赞明细实体，对应 comment_like 表。
 * 自增 PK + UNIQUE(comment_id, user_id)：照 resource_like 范式（一人一评论一条，toggle 依据）。
 * 比 blog_like（复合主键）多一个 like_id，但语义一致——点赞=插入 ignore，取消=删除。
 * 不继承 BaseEntity（轻量 POJO，仅 4 字段，照 BlogLike.java）。
 */
public class CommentLike {

    private Long likeId;

    private Long commentId;

    private Long userId;

    private Date createTime;

    public CommentLike() {
    }

    public CommentLike(Long commentId, Long userId) {
        this.commentId = commentId;
        this.userId = userId;
    }

    public Long getLikeId() {
        return likeId;
    }

    public void setLikeId(Long likeId) {
        this.likeId = likeId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
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
        return "CommentLike{" +
                "likeId=" + likeId +
                ", commentId=" + commentId +
                ", userId=" + userId +
                '}';
    }
}