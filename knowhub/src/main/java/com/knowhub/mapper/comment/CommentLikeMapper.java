package com.knowhub.mapper.comment;

import com.knowhub.pojo.comment.entity.CommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评论点赞明细 Mapper，照 resource_like 范式（自增 PK + UNIQUE(comment_id,user_id)）。
 */
@Mapper
public interface CommentLikeMapper {

    /** 点赞（insert ignore，主键冲突视为已点赞） */
    int addCommentLike(CommentLike commentLike);

    /** 取消点赞（删除） */
    int deleteCommentLike(CommentLike commentLike);

    /** 查询某用户是否已点赞某评论 */
    CommentLike getCommentLike(CommentLike commentLike);

    /** 删评论时连带清理点赞记录（service 删顶级/回复时同步清） */
    int deleteByCommentId(@Param("commentId") Long commentId);
}