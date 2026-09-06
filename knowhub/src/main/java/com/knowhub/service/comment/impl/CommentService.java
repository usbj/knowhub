package com.knowhub.service.comment.impl;

import com.knowhub.pojo.comment.vo.CommentCreateVo;
import com.knowhub.pojo.comment.vo.CommentReviewVo;

/**
 * 评论写接口（POST/DELETE/PUT，走 /authoring/comment/**，authenticated 兜底）。
 * <p>
 * 职责：发评论 / 删评论 / 点赞 / 作者 inline 精选审核。
 * 不暴露实体，仅入参 VO + 标量出参。
 */
public interface CommentService {

    /**
     * 发评论（顶级或回复）。
     * <ul>
     *   <li>校验 bizType 枚举 + 主表存在 + comment_enabled=1（关了拒）。</li>
     *   <li>能看作品才能评论：登录用户须为作者 OR 主表 PUBLISHED（前台门户只下发已发布作品，
     *       评论列表权限谓词本身已 gate 不可见作品），故此处不另起 level→userViewLevel 判定。</li>
     *   <li>parentId 非空：校验 parent 存在、parent.parent_id IS NULL（拒三层）、parent.biz_type+biz_id 与入参一致。</li>
     *   <li>review_status：主表 comment_curated=1→PENDING（仅作者+本人可见）；=0→NONE（直接可见）。</li>
     *   <li>replyToUserId/replyToNickname 仅 parentId 非空且 replyToUserId ≠ 楼主 author_id 时填（@其他用户）。</li>
     * </ul>
     *
     * @return 新评论 commentId
     */
    Long createComment(CommentCreateVo vo);

    /**
     * 删评论（决策#6）：
     * <ul>
     *   <li>admin 短路。</li>
     *   <li>作品作者可删该作品下任意人评论（校验 comment.biz 主表 author_id==currentUser）。</li>
     *   <li>否则仅评论 author_id==currentUser（自删）。</li>
     *   <li>删顶级：连带其下回复软删 deleted=1（不保留壳帖）。</li>
     * </ul>
     */
    Boolean deleteComment(Long commentId);

    /** 点赞/取消点赞评论（liked=true 点赞，false 取消）。照 blog toggleLike 范式，事务内同步 like_count 冗余列。 */
    Boolean toggleLike(Long commentId, Boolean liked);

    /**
     * 作者 inline 精选审核（决策#2）：
     * <ul>
     *   <li>校验 comment.biz 主表 author_id==currentUser（非作者无权精选→拒）。</li>
     *   <li>action=APPROVE→review_status=APPROVED；action=REJECT→review_status=REJECTED。</li>
     *   <li>仅当 comment 当前 PENDING 才允许动作（NONE/APPROVED/REJECTED 的重复动作返 false）。</li>
     *   <li>不发通知（决策#10），快照落 reviewer/review_time/review_advice 列。</li>
     * </ul>
     */
    Boolean reviewComment(Long commentId, CommentReviewVo vo);
}