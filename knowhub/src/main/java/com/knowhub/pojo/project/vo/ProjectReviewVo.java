package com.knowhub.pojo.project.vo;

/**
 * 项目审核入参 VO（与资源 ResourceReviewVo 结构同，字段名 project 化）。
 * pass=true 通过 → PUBLISHED；pass=false 驳回 → REJECTED（advice 必填）。
 */
public class ProjectReviewVo {

    private Long projectId;

    private Boolean pass;

    private String advice;

    public ProjectReviewVo() {
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
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
        return "ProjectReviewVo{" +
                "projectId=" + projectId +
                ", pass=" + pass +
                ", advice='" + advice + '\'' +
                '}';
    }
}
