package com.knowhub.mapper.comment;

import com.knowhub.pojo.comment.entity.Comment;
import com.knowhub.pojo.comment.quarry.CommentListQuarry;
import com.knowhub.pojo.comment.quarry.ReplyListQuarry;
import com.knowhub.pojo.comment.vo.CommentPortalVo;
import com.knowhub.pojo.comment.vo.CommentReplyVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 统一评论 Mapper（博客/文章/项目/资源四类作品共用 comment 表）。
 */
@Mapper
public interface CommentMapper {

    /** 新增评论（动态列），主键回填 commentId */
    int addComment(Comment comment);

    /** 按主键取评论（权限校验/精选/删除用） */
    Comment getCommentById(@Param("commentId") Long commentId);

    /**
     * 顶级评论分页（指定作品下）。
     * 权限谓词由 quarry.currentUserId / workAuthorId 透传 SQL：
     * 「review_status IN (NONE,APPROVED) OR author_id=currentUserId OR workAuthorId=currentUserId」
     */
    List<CommentPortalVo> listTopComments(CommentListQuarry quarry);

    /** 某顶级评论下的回复分页（权限谓词同上） */
    List<CommentReplyVo> listReplies(ReplyListQuarry quarry);

    /** 批量取多个顶级评论的回复数（service 回填 replyCount，防 N+1），返回 {commentId, cnt} */
    List<Map<String, Object>> countRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    /** 软删：删顶级评论本身 deleted=1 */
    int softDeleteComment(@Param("commentId") Long commentId);

    /** 删顶级评论连带其下回复 deleted=1（service 删顶级时调一次，不保留壳帖） */
    int softDeleteRepliesByParent(@Param("parentId") Long parentId);

    /** 点赞数自增/自减（service 同步 comment_like 时 +1/-1） */
    int incrLikeCount(@Param("commentId") Long commentId, @Param("delta") Long delta);

    /** 作者 inline 精选：更新审核快照（reviewer/review_time/review_advice/review_status） */
    int updateReview(@Param("commentId") Long commentId,
                     @Param("reviewStatus") String reviewStatus,
                     @Param("reviewer") Long reviewer,
                     @Param("reviewAdvice") String reviewAdvice);

    /** 批量取当前用户对给定评论的点赞状态（service 回填 hasLiked，防 N+1），返回 {commentId, like_id} */
    List<Map<String, Object>> getLikedByUser(@Param("commentIds") List<Long> commentIds,
                                             @Param("userId") Long userId);
}