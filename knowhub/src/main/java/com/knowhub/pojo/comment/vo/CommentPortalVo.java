package com.knowhub.pojo.comment.vo;

import java.util.Date;

/**
 * 前台评论列表项 VO（顶级评论，供 /portal/comment/list 出参）。
 * <p>
 * 列表 SQL join sys_user 带 authorNickname；hasLiked 由 service 批量回填（登录态查 comment_like，
 * 未登录为 null）；replyCount 由 service 子查询/批量回填（该顶级评论下的回复数，含待精仅作者+本人可见）。
 * <p>
 * reviewStatus：
 * - NONE/APPROVED：前台对所有人正常展示。
 * - PENDING：仅本人 + 作品作者可见（SQL 谓词已下发），前端对本人额外显示「待作者确认」标签。
 * - REJECTED：仅本人 + 作品作者可见，前端对本人显示「已拒绝展示」+ reviewAdvice（如有）。
 * 作者 inline 审核按钮由前端按作品作者态渲染（非此 VO 字段），后端谓词保证作者天然看到 PENDING 评论。
 */
public class CommentPortalVo {

    private Long commentId;

    /** 评论发起人 userId */
    private Long authorId;

    /** 评论人昵称（join sys_user on user_id=author_id 带出） */
    private String authorNickname;

    /** 评论内容（纯文本，前端限长 2000） */
    private String content;

    /** 创建时间（service 用 createTime 回填，jackson 全局格式化 yyyy-MM-dd HH:mm:ss） */
    private Date createTime;

    /** 点赞数（冗余列读快） */
    private Long likeCount;

    /** 当前用户是否已点赞（登录态回填，未登录为 null） */
    private Boolean hasLiked;

    /** 该顶级评论下的回复数（service 批量回填） */
    private Integer replyCount;

    /**
     * 审核状态：NONE 直接可见 / PENDING 待作者确认(仅本人+作者) / APPROVED 通过 / REJECTED 已拒绝。
     * 前端据本人态与作者态决定是否展示状态标签与作者操作按钮。
     */
    private String reviewStatus;

    /** 审核意见（REJECTED 时本人可见原因，可空） */
    private String reviewAdvice;

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public Boolean getHasLiked() {
        return hasLiked;
    }

    public void setHasLiked(Boolean hasLiked) {
        this.hasLiked = hasLiked;
    }

    public Integer getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(Integer replyCount) {
        this.replyCount = replyCount;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewAdvice() {
        return reviewAdvice;
    }

    public void setReviewAdvice(String reviewAdvice) {
        this.reviewAdvice = reviewAdvice;
    }
}