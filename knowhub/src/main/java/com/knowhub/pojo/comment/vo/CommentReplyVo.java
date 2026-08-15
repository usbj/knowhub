package com.knowhub.pojo.comment.vo;

import java.util.Date;

/**
 * 回复列表项 VO（顶级评论下的回复，供 /portal/comment/replies/{commentId} 出参）。
 * <p>
 * 减 replyCount（回复不嵌套回复）；加 replyToUserId / replyToNickname（@某楼内某用户时的快照）。
 * 权限谓词与 {@link CommentPortalVo} 一致：PENDING/REJECTED 仅发表人+作品作者可见。
 */
public class CommentReplyVo {

    private Long commentId;

    private Long authorId;

    /** 评论人昵称（join sys_user 带出） */
    private String authorNickname;

    private String content;

    private Date createTime;

    private Long likeCount;

    /** 当前用户是否已点赞（登录态回填，未登录为 null） */
    private Boolean hasLiked;

    /** @某楼内某用户时的 userId（直接回复楼主为 null） */
    private Long replyToUserId;

    /** @人昵称快照（展示用） */
    private String replyToNickname;

    private String reviewStatus;

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

    public Long getReplyToUserId() {
        return replyToUserId;
    }

    public void setReplyToUserId(Long replyToUserId) {
        this.replyToUserId = replyToUserId;
    }

    public String getReplyToNickname() {
        return replyToNickname;
    }

    public void setReplyToNickname(String replyToNickname) {
        this.replyToNickname = replyToNickname;
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