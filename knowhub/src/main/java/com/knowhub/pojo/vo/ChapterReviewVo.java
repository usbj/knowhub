package com.knowhub.pojo.vo;

/**
 * 章节作者审核入参 VO（仅 SEMIPUBLIC 文章场景用，文章作者审非作者提交的章节）。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 * 仅 PENDING_AUTHOR_REVIEW 态可审；审核人必须是该文章作者（article.author_id 比对）。
 * 不受系统审核开关 knowhub.article.review_enabled 影响（visibility tier 固有机制）。
 */
public class ChapterReviewVo {

    private Long chapterId;

    private Boolean pass;

    private String advice;

    public ChapterReviewVo() {
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Boolean getPass() {
        return pass;
    }

    public void setPass(Boolean pass) {
        this.pass = pass;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    @Override
    public String toString() {
        return "ChapterReviewVo{" +
                "chapterId=" + chapterId +
                ", pass=" + pass +
                ", advice='" + advice + '\'' +
                '}';
    }
}
