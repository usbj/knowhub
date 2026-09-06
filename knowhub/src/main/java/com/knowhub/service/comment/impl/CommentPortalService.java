package com.knowhub.service.comment.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.comment.vo.CommentPortalVo;
import com.knowhub.pojo.comment.vo.CommentReplyVo;

/**
 * 评论读接口（GET，走 /portal/comment/**，permitAll）。
 * <p>
 * 列表/回复分页：防御性取登录态（未登录 currentUserOrNull 返回 null），
 * 按作品作者+当前用户拼 SQL 权限谓词（NONE/APPROVED 或 本人 或 该作品作者）。
 */
public interface CommentPortalService {

    /** 顶级评论分页（指定作品下，order=new/like） */
    PageInfo<CommentPortalVo> listComments(String bizType, Long bizId, String order);

    /** 某顶级评论下的回复分页（两层嵌套第二层） */
    PageInfo<CommentReplyVo> listReplies(Long commentId);
}