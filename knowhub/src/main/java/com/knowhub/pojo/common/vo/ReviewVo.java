package com.knowhub.pojo.common.vo;

/**
 * 博客审核入参 VO。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 */
public class ReviewVo {

    private Long blogId;

    private Boolean pass;

    private String advice;

    public ReviewVo() {
    }

    public Long getBlogId() {
        return blogId;
    }

    public void setBlogId(Long blogId) {
        this.blogId = blogId;
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
        return "ReviewVo{" +
                "blogId=" + blogId +
                ", pass=" + pass +
                ", advice='" + advice + '\'' +
                '}';
    }
}