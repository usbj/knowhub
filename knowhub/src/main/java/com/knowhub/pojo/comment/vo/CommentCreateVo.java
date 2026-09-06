package com.knowhub.pojo.comment.vo;

/**
 * 发评论入参 VO（POST /authoring/comment）。
 * <p>
 * bizType + bizId + content 必填；parentId 回复顶级评论时填（填了即回复，留空即顶级）。
 * replyToUserId 在 parentId 非空且回复的是某楼内其他用户（非楼主）时填，service 自取该用户昵称写 reply_to_nickname 快照。
 * 直接回复楼主时 replyToUserId 传 null。
 */
public class CommentCreateVo {

    /** 业务类型 BLOG/ARTICLE/PROJECT/RESOURCE */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /**
     * 评论内容（Markdown 评论；含图片为 inline ![](/file/resolve/{id}) 相对路径；限长 2000，
     * service 仅去空 + 长度校验，不转义原样落库）。
     */
    private String content;

    /** 父评论ID：回复顶级评论时填，留空即发顶级评论 */
    private Long parentId;

    /** @某楼内某用户的 userId（仅 parentId 非空且回复非楼主时填） */
    private Long replyToUserId;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}