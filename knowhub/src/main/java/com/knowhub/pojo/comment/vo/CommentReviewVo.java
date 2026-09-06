package com.knowhub.pojo.comment.vo;

/**
 * 作者 inline 精选审核入参 VO（POST /authoring/comment/{commentId}/review）。
 * <p>
 * action=APPROVE→review_status=APPROVED（他人可见）；action=REJECT→review_status=REJECTED（仍仅作者+本人可见）。
 * advice 审核意见可选（REJECT 时可留空）；api 仅作者本人能调（service 校验 comment.biz 主表 author_id==currentUser）。
 */
public class CommentReviewVo {

    /** 动作：APPROVE 同意展示 / REJECT 拒绝 */
    private String action;

    /** 审核意见（可选） */
    private String advice;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }
}