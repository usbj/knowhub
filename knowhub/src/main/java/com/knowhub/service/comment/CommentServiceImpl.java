package com.knowhub.service.comment;

import com.knowhub.enums.comment.CommentBizType;
import com.knowhub.enums.common.ReviewAction;
import com.knowhub.enums.common.ReviewStatus;
import com.knowhub.mapper.comment.CommentLikeMapper;
import com.knowhub.mapper.comment.CommentMapper;
import com.knowhub.pojo.comment.entity.Comment;
import com.knowhub.pojo.comment.entity.CommentLike;
import com.knowhub.pojo.comment.vo.CommentCreateVo;
import com.knowhub.pojo.comment.vo.CommentReviewVo;
import com.knowhub.service.comment.impl.CommentService;
import com.knowhub.support.ArticlePermissionResolver;
import com.knowhub.support.BlogPermissionResolver;
import com.knowhub.support.CommentWorkResolver;
import com.knowhub.support.CommentWorkResolver.WorkMeta;
import com.knowhub.support.NotifySupport;
import com.knowhub.support.ProjectPermissionResolver;
import com.knowhub.support.ResourcePermissionResolver;
import com.rookie.common.exception.ServiceException;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 评论写 Service 实现（创作/删除/点赞/作者 inline 精选）。
 * <p>
 * 走 /authoring/comment/**（authenticated 兜底），principal 是 UserInfo，{@link #currentUser()} 直接强转安全。
 * 作品元数据（作者/开关）由 {@link CommentWorkResolver} 统一路由解析，避免跨耦合各内容模块 PortalService。
 * 删顶级评论连带其下回复软删（不保留壳帖），同步清点赞事实记录。
 */
@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    CommentMapper commentMapper;

    @Autowired
    CommentLikeMapper commentLikeMapper;

    @Autowired
    CommentWorkResolver workResolver;

    /** 评论回复通知薄封装（回复路径给被回复人发站内通知，user 指示「直接在 knowhub 模块加接口」）。 */
    @Autowired
    NotifySupport notifySupport;

    @Override
    @Transactional
    public Long createComment(CommentCreateVo vo) {
        if (vo == null || vo.getBizType() == null || vo.getBizId() == null) {
            throw new ServiceException(500, "参数缺失");
        }
        CommentBizType type = parseBizType(vo.getBizType());
        if (type == null) {
            throw new ServiceException(500, "非法业务类型");
        }
        if (vo.getContent() == null || vo.getContent().trim().isEmpty()) {
            throw new ServiceException(500, "评论内容不能为空");
        }
        if (vo.getContent().length() > 2000) {
            throw new ServiceException(500, "评论内容过长");
        }

        WorkMeta work = workResolver.resolve(vo.getBizType(), vo.getBizId());
        if (!work.exists()) {
            throw new ServiceException(500, "作品不存在或已删除");
        }
        // 评论区关闭：拒发
        if (work.commentEnabled() != null && work.commentEnabled() == 0) {
            throw new ServiceException(500, "评论区已关闭");
        }

        UserInfo user = currentUser();
        Long userId = user.getUserId();

        // 越级锁评论（2026-08-18）：越级用户能看作品（列表带 locked 标记、详情预览式锁）但不能发评论——
        // 看不了完整内容却发评论不合常理。作者本人放行（作者天然有全权，即便自己等级<作品 level 也能评论自己作品）。
        // admin 走各 resolver 自然得 level 3 全过；未授权者得 0 只能评论 L1 作品。
        if (work.level() != null && !userId.equals(work.authorId())) {
            int userLevel = resolveWorkLevel(type);
            if (userLevel < work.level()) {
                throw new ServiceException(500, "等级不足，无法评论该作品（需 L" + work.level() + " 权限）");
            }
        }

        Comment parent = null;
        if (vo.getParentId() != null) {
            parent = commentMapper.getCommentById(vo.getParentId());
            if (parent == null || (parent.getDeleted() != null && parent.getDeleted() == 1)) {
                throw new ServiceException(500, "回复目标评论不存在");
            }
            // 拒三层：parent 必须是顶级（parent.parent_id IS NULL）
            if (parent.getParentId() != null) {
                throw new ServiceException(500, "仅支持两级评论");
            }
            // parent 必须归属同一作品
            if (!vo.getBizType().equals(parent.getBizType()) || !vo.getBizId().equals(parent.getBizId())) {
                throw new ServiceException(500, "回复目标评论与作品不匹配");
            }
        }

        // 审核状态：作者开精选→PENDING（仅作者+本人可见）；否则→NONE（直接全员可见）
        boolean curated = work.commentCurated() != null && work.commentCurated() == 1;
        String reviewStatus = curated ? ReviewStatus.PENDING.getCode() : ReviewStatus.NONE.getCode();

        Comment record = new Comment();
        record.setBizType(vo.getBizType());
        record.setBizId(vo.getBizId());
        record.setAuthorId(userId);
        record.setContent(vo.getContent());
        record.setReviewStatus(reviewStatus);
        record.setCreateBy(user.getUsername());
        record.setUpdateBy(user.getUsername());
        record.setLikeCount(0L);

        if (parent != null) {
            record.setParentId(vo.getParentId());
            // @某人：仅 replyToUserId 非空且回复非楼主（楼主由楼主看到，不需显式 @）时填昵称快照
            Long replyToUserId = vo.getReplyToUserId();
            if (replyToUserId != null && !replyToUserId.equals(parent.getAuthorId())) {
                record.setReplyToUserId(replyToUserId);
                record.setReplyToNickname(workResolver.nicknameOf(replyToUserId));
            }
        }

        commentMapper.addComment(record);

        // 回复通知：仅回复路径（parent != null）发，顶级评论落库即全员可见无被回复人概念。
        // 被回复人 = record.replyToUserId（@某楼内其他用户，L100-108 仅此情况非 null）?? parent.authorId（直回楼主）。
        // 自回复跳过（target == 当前用户）。通知失败由 NotifySupport 内部 try/catch 吞掉，不阻断评论落库。
        if (parent != null) {
            Long notifyTarget = (record.getReplyToUserId() != null)
                    ? record.getReplyToUserId()
                    : parent.getAuthorId();
            if (notifyTarget != null && !notifyTarget.equals(userId)) {
                String replierNick = workResolver.nicknameOf(userId);
                String title = (replierNick != null && !replierNick.isBlank() ? replierNick : "有人") + " 回复了你的评论";
                String content = truncate(record.getContent(), 50);
                String routePath = workRoute(vo.getBizType(), vo.getBizId());
                notifySupport.notifyUser(notifyTarget, title, content, routePath, user.getUsername());
            }
        }

        return record.getCommentId();
    }

    @Override
    @Transactional
    public Boolean deleteComment(Long commentId) {
        if (commentId == null) {
            throw new ServiceException(500, "参数缺失");
        }
        Comment comment = commentMapper.getCommentById(commentId);
        if (comment == null || (comment.getDeleted() != null && comment.getDeleted() == 1)) {
            return false;
        }
        UserInfo user = currentUser();

        // 权限：admin 短路 OR 作品作者删该作品下任意评论 OR 自删；都不满足拒
        boolean isAdmin = user.isAdmin();
        boolean isSelf = comment.getAuthorId() != null && comment.getAuthorId().equals(user.getUserId());
        boolean isWorkAuthor = false;
        WorkMeta work = workResolver.resolve(comment.getBizType(), comment.getBizId());
        if (work.exists() && work.authorId() != null) {
            isWorkAuthor = work.authorId().equals(user.getUserId());
        }
        if (!(isAdmin || isWorkAuthor || isSelf)) {
            throw new ServiceException(500, "无权删除该评论");
        }

        // 删顶级：连带其下回复软删（不保留壳帖）；回复则只删自己
        if (comment.getParentId() == null) {
            commentMapper.softDeleteRepliesByParent(commentId);
        }
        commentMapper.softDeleteComment(commentId);
        // 同步清理本条点赞事实记录（回复的点赞记录留下亦无伤——冗余列已随 deleted=1 不可见，事实表不泄读）
        commentLikeMapper.deleteByCommentId(commentId);
        return true;
    }

    @Override
    @Transactional
    public Boolean toggleLike(Long commentId, Boolean liked) {
        if (commentId == null) {
            throw new ServiceException(500, "参数缺失");
        }
        Comment comment = commentMapper.getCommentById(commentId);
        if (comment == null || (comment.getDeleted() != null && comment.getDeleted() == 1)) {
            throw new ServiceException(500, "评论不存在");
        }
        Long userId = currentUser().getUserId();
        CommentLike record = new CommentLike(commentId, userId);
        if (Boolean.TRUE.equals(liked)) {
            CommentLike old = commentLikeMapper.getCommentLike(record);
            commentLikeMapper.addCommentLike(record);
            if (old == null) {
                commentMapper.incrLikeCount(commentId, 1L);
            }
        } else {
            CommentLike old = commentLikeMapper.getCommentLike(record);
            commentLikeMapper.deleteCommentLike(record);
            if (old != null) {
                commentMapper.incrLikeCount(commentId, -1L);
            }
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean reviewComment(Long commentId, CommentReviewVo vo) {
        if (commentId == null || vo == null || vo.getAction() == null) {
            throw new ServiceException(500, "参数缺失");
        }
        String action = vo.getAction();
        String targetStatus;
        if (ReviewAction.APPROVE.getCode().equals(action)) {
            targetStatus = ReviewStatus.APPROVED.getCode();
        } else if (ReviewAction.REJECT.getCode().equals(action)) {
            targetStatus = ReviewStatus.REJECTED.getCode();
        } else {
            throw new ServiceException(500, "非法审核动作");
        }

        Comment comment = commentMapper.getCommentById(commentId);
        if (comment == null || (comment.getDeleted() != null && comment.getDeleted() == 1)) {
            throw new ServiceException(500, "评论不存在");
        }
        // 仅 PENDING 可精选；其它状态重复动作返 false
        if (!ReviewStatus.PENDING.getCode().equals(comment.getReviewStatus())) {
            return false;
        }
        // 权限：仅该作品作者可 inline 精选（非作者拒）
        WorkMeta work = workResolver.resolve(comment.getBizType(), comment.getBizId());
        if (!work.exists() || work.authorId() == null || !work.authorId().equals(currentUser().getUserId())) {
            throw new ServiceException(500, "无权审核该评论");
        }

        commentMapper.updateReview(commentId, targetStatus, currentUser().getUserId(), vo.getAdvice());
        return true;
    }

    // ============================ 私有辅助 ============================

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

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * 按 bizType 分派取当前用户对该类作品的查看等级（单键 knowhub:xxx:lN，resolver 扫 perms 取最高）。
     * 评论越级闸用——越级用户（userLevel < work.level）不能发评论。admin 自然得 3 全过。
     */
    private int resolveWorkLevel(CommentBizType type) {
        return switch (type) {
            case BLOG -> BlogPermissionResolver.resolve().level();
            case ARTICLE -> ArticlePermissionResolver.resolve().level();
            case PROJECT -> ProjectPermissionResolver.resolve().level();
            case RESOURCE -> ResourcePermissionResolver.resolve().level();
        };
    }

    /** 通知正文截断：超长切前 max 字符追加 "…"，避免长评论撑爆通知流；null/空返空串。 */
    private static String truncate(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    /** 作品 bizType → 前台作品详情路由前缀静态映射，拼 bizId 成 /blog/{id} 等（走 CommentBizType 码字符串口径） */
    private static final Map<String, String> WORK_ROUTE_PREFIX = Map.of(
            "BLOG", "/blog/",
            "ARTICLE", "/article/",
            "PROJECT", "/project/",
            "RESOURCE", "/resource/"
    );

    /** 拼 bizType+bizId 对应前台作品详情路由（供回复通知 routePath，点击「前往查看」跳作品详情）。 */
    private static String workRoute(String bizType, Long bizId) {
        String prefix = WORK_ROUTE_PREFIX.get(bizType);
        if (prefix == null || bizId == null) {
            return null;
        }
        return prefix + bizId;
    }
}