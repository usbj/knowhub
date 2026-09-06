package com.knowhub.pojo.comment.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 统一评论实体，对应 comment 表（博客/文章/项目/资源四类作品共用）。
 * <p>
 * 两层嵌套：parent_id=NULL 即顶级评论，非空指向顶级 comment_id（只两层，service 拒三层）。
 * reply_to_user_id / reply_to_nickname 在回复某楼内某用户时填（@某人），直接回复楼主为 NULL。
 * <p>
 * 评论精选开关式审核（无专门审核页）：
 * - 作者未开精选 → review_status 恒 NONE（直接全员可见）。
 * - 作者开精选    → 新评论 review_status=PENDING，仅发表人 + 该作品作者可见；
 *   作者在评论区 inline「同意展示」(APPROVED 后他人可见)/「拒绝」(REJECTED 仍仅作者+发表人可见)/或直接删除。
 * 审核动作快照 reviewer/review_time/review_advice 落主表（不另建流水表，无审核历史查询需求）。
 * <p>
 * like_count 冗余列（以 comment_like 事实表为准，service 同步 +1/-1，列表快读免 N 次 count）。
 * deleted 软删：删顶级评论 service 连带其下回复 deleted=1（不保留壳帖）。
 * <p>
 * author_id 为评论发起人 userId（对标各主表 author_id），与.createBy(username 快照) 互补：
 * createBy 存账号串便于直显，authorId 用 userId 稳定锁定（username 可改，userId 不变），
 * 前台展示评论人昵称走 join sys_user on user_id=author_id。
 * 审计列(createBy/updateBy/createTime/updateTime) 由 BaseEntity 承载。
 */
public class Comment extends BaseEntity {

    private Long commentId;

    /** 业务类型 BLOG/ARTICLE/PROJECT/RESOURCE（见 CommentBizType 枚举，不入字典） */
    private String bizType;

    /** 业务ID（各主表主键） */
    private Long bizId;

    /** 评论发起人 userId */
    private Long authorId;

    /** 父评论ID：顶级评论为 NULL；回复指向顶级 comment_id（仅两层） */
    private Long parentId;

    /** @人用户ID（回复某楼内某用户时填，直接回复楼主为 NULL） */
    private Long replyToUserId;

    /** @人昵称快照（展示用，username 改动不影响） */
    private String replyToNickname;

    /**
     * 评论内容（Markdown，含配图为 ![](/file/resolve/{id}) 相对引用；限长 2000，
     * 其中配图 9 张以内约 234 字符不撑爆 varchar(2000)，service 仅去空 + 长度校验不做 markdown/HTML 转义，原样落库）。
     */
    private String content;

    /** 点赞数（冗余列，以 comment_like 为准，service 同步 +1/-1） */
    private Long likeCount;

    /**
     * 审核状态：复用字典 review_status。
     * NONE=直接可见(作者未开精选恒 NONE) / PENDING=待精仅作者+本人可见 /
     * APPROVED=作者同意展示全员可见 / REJECTED=作者拒绝仍仅作者+本人可见。
     */
    private String reviewStatus;

    /** 审核人 userId（作者精选时填作者自身 userId） */
    private Long reviewer;

    private Date reviewTime;

    /** 审核意见（仅作者精选动作时填，拒绝可留空） */
    private String reviewAdvice;

    private Integer deleted;

    // ---- 非表字段（列表/详情查询 join 带出的展示字段，resultMap 映射，不入库） ----
    /** 评论人昵称（join sys_user on user_id=author_id 带出，非表字段） */
    private String authorNickname;

    public Comment() {
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Long getReviewer() {
        return reviewer;
    }

    public void setReviewer(Long reviewer) {
        this.reviewer = reviewer;
    }

    public Date getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime) {
        this.reviewTime = reviewTime;
    }

    public String getReviewAdvice() {
        return reviewAdvice;
    }

    public void setReviewAdvice(String reviewAdvice) {
        this.reviewAdvice = reviewAdvice;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public void setAuthorNickname(String authorNickname) {
        this.authorNickname = authorNickname;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "commentId=" + commentId +
                ", bizType='" + bizType + '\'' +
                ", bizId=" + bizId +
                ", authorId=" + authorId +
                ", parentId=" + parentId +
                ", reviewStatus='" + reviewStatus + '\'' +
                ", deleted=" + deleted +
                '}';
    }
}