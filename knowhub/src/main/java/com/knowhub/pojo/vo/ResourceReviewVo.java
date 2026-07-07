package com.knowhub.pojo.vo;

/**
 * 资源审核入参 VO（与博客 ReviewVo 结构同，字段名 resource 化）。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 */
public class ResourceReviewVo {

    private Long resourceId;

    private Boolean pass;

    private String advice;

    public ResourceReviewVo() {
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
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
        return "ResourceReviewVo{" +
                "resourceId=" + resourceId +
                ", pass=" + pass +
                ", advice='" + advice + '\'' +
                '}';
    }
}
