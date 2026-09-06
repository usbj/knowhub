package com.knowhub.service.comment;

import com.github.pagehelper.PageInfo;
import com.knowhub.enums.comment.CommentBizType;
import com.knowhub.mapper.comment.CommentMapper;
import com.knowhub.pojo.comment.entity.Comment;
import com.knowhub.pojo.comment.quarry.CommentListQuarry;
import com.knowhub.pojo.comment.quarry.ReplyListQuarry;
import com.knowhub.pojo.comment.vo.CommentPortalVo;
import com.knowhub.pojo.comment.vo.CommentReplyVo;
import com.knowhub.service.comment.impl.CommentPortalService;
import com.knowhub.support.CommentWorkResolver;
import com.knowhub.support.CommentWorkResolver.WorkMeta;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评论读 Service 实现（/portal/comment/** permitAll 区）。
 * <p>
 * 防御性取登录态（principal 非 UserInfo 视未登录）；按作品作者+当前用户拼 SQL 权限谓词：
 * NONE/APPROVED 或 本人 或 该作品作者（作者 inline 看到并操作 PENDING）。
 * hasLiked 登录态批量回填（comment_like），未登录为 null；replyCount 批量回填（顶级评论）。
 */
@Service
public class CommentPortalServiceImpl implements CommentPortalService {

    @Autowired
    CommentMapper commentMapper;

    @Autowired
    CommentWorkResolver workResolver;

    @Override
    public PageInfo<CommentPortalVo> listComments(String bizType, Long bizId, String order) {
        if (bizType == null || bizId == null) {
            throw new ServiceException(500, "参数缺失");
        }
        if (parseBizType(bizType) == null) {
            throw new ServiceException(500, "非法业务类型");
        }
        UserInfo user = currentUserOrNull();
        WorkMeta work = workResolver.resolve(bizType, bizId);
        // 作品不存在：返空列表（前台本不会展示评论组件，防御性返空）
        if (!work.exists()) {
            return emptyPage();
        }
        // 评论区关闭：返空列表（不展示）
        if (work.commentEnabled() != null && work.commentEnabled() == 0) {
            return emptyPage();
        }

        CommentListQuarry quarry = new CommentListQuarry();
        quarry.setBizType(bizType);
        quarry.setBizId(bizId);
        quarry.setOrder(order);
        quarry.setCurrentUserId(user == null ? null : user.getUserId());
        quarry.setWorkAuthorId(work.authorId());

        PageUtil.startPage();
        List<CommentPortalVo> list = commentMapper.listTopComments(quarry);

        // 批量回填 replyCount + hasLiked（防 N+1）
        if (!list.isEmpty()) {
            List<Long> commentIds = list.stream().map(CommentPortalVo::getCommentId).toList();
            fillReplyCount(list, commentIds);
            if (user != null) {
                fillHasLiked(list, commentIds, user.getUserId());
            } else {
                list.forEach(c -> c.setHasLiked(null));
            }
        }
        return new PageInfo<>(list);
    }

    @Override
    public PageInfo<CommentReplyVo> listReplies(Long commentId) {
        if (commentId == null) {
            throw new ServiceException(500, "参数缺失");
        }
        Comment parent = commentMapper.getCommentById(commentId);
        if (parent == null || (parent.getDeleted() != null && parent.getDeleted() == 1)) {
            // 顶级评论不存在/已删：回复列表返空
            return emptyPage();
        }
        if (parent.getParentId() != null) {
            // 入参本身是回复而非法顶级：返空（回复不嵌套回复）
            return emptyPage();
        }
        WorkMeta work = workResolver.resolve(parent.getBizType(), parent.getBizId());
        if (!work.exists() || (work.commentEnabled() != null && work.commentEnabled() == 0)) {
            // 作品已删或评论区关：返空
            return emptyPage();
        }

        UserInfo user = currentUserOrNull();
        ReplyListQuarry quarry = new ReplyListQuarry();
        quarry.setParentId(commentId);
        quarry.setCurrentUserId(user == null ? null : user.getUserId());
        quarry.setWorkAuthorId(work.authorId());

        PageUtil.startPage();
        List<CommentReplyVo> list = commentMapper.listReplies(quarry);

        if (!list.isEmpty() && user != null) {
            List<Long> commentIds = list.stream().map(CommentReplyVo::getCommentId).toList();
            fillHasLikedForReply(list, commentIds, user.getUserId());
        }
        return new PageInfo<>(list);
    }

    // ============================ 私有辅助 ============================

    /** 批量回填顶级评论的回复数（防 N+1） */
    private void fillReplyCount(List<CommentPortalVo> list, List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            list.forEach(c -> c.setReplyCount(0));
            return;
        }
        List<Map<String, Object>> rows = commentMapper.countRepliesByParentIds(commentIds);
        Map<Long, Integer> cntMap = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Long pid = toLong(row.get("commentId"));
                Integer cnt = toInt(row.get("cnt"));
                if (pid != null) {
                    cntMap.put(pid, cnt == null ? 0 : cnt);
                }
            }
        }
        for (CommentPortalVo vo : list) {
            vo.setReplyCount(cntMap.getOrDefault(vo.getCommentId(), 0));
        }
    }

    /** 批量回填顶级评论 hasLiked（防 N+1） */
    private void fillHasLiked(List<CommentPortalVo> list, List<Long> commentIds, Long userId) {
        Set<Long> likedIds = likedIdsOf(commentIds, userId);
        for (CommentPortalVo vo : list) {
            vo.setHasLiked(likedIds.contains(vo.getCommentId()));
        }
    }

    /** 批量回填回复 hasLiked（防 N+1） */
    private void fillHasLikedForReply(List<CommentReplyVo> list, List<Long> commentIds, Long userId) {
        Set<Long> likedIds = likedIdsOf(commentIds, userId);
        for (CommentReplyVo vo : list) {
            vo.setHasLiked(likedIds.contains(vo.getCommentId()));
        }
    }

    private Set<Long> likedIdsOf(List<Long> commentIds, Long userId) {
        if (commentIds == null || commentIds.isEmpty() || userId == null) {
            return Collections.emptySet();
        }
        List<Map<String, Object>> rows = commentMapper.getLikedByUser(commentIds, userId);
        Set<Long> liked = new HashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Long cid = toLong(row.get("commentId"));
                if (cid != null) {
                    liked.add(cid);
                }
            }
        }
        return liked;
    }

    private CommentBizType parseBizType(String code) {
        if (code == null) {
            return null;
        }
        for (CommentBizType t : CommentBizType.values()) {
            if (t.getCode().equals(code)) {
                return t;
            }
        }
        return null;
    }

    private <T> PageInfo<T> emptyPage() {
        PageInfo<T> p = new PageInfo<>(new ArrayList<>());
        return p;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    /**
     * 防御性取当前登录用户：principal 是 UserInfo 才返回，否则 null（未登录/匿名）。
     * 前台 permitAll 区不能像后台那样直接强转（会抛异常），照 BlogPortalServiceImpl 范式。
     */
    private UserInfo currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        return (p instanceof UserInfo) ? (UserInfo) p : null;
    }
}