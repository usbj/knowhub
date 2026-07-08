package com.knowhub.pojo.vo;

/**
 * 比赛项目子表 VO，供 Controller 入参/出参（按 type=COMPETITION 取，与 ProjectVo 配套）。
 * 不继承 BaseEntity（无独立审计列，随主表）；时间字段用 java.util.Date。
 * 前端表单按 type 动态渲染对应子表字段，主表 type 切换时子表字段跟着切换。
 */
public class ProjectCompetitionVo {

    private Long projectId;

    private String competitionName;

    private String competitionLevel;

    private String awardLevel;

    private java.util.Date awardTime;

    private java.util.Date competitionTime;

    public ProjectCompetitionVo() {
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getCompetitionName() {
        return competitionName;
    }

    public void setCompetitionName(String competitionName) {
        this.competitionName = competitionName;
    }

    public String getCompetitionLevel() {
        return competitionLevel;
    }

    public void setCompetitionLevel(String competitionLevel) {
        this.competitionLevel = competitionLevel;
    }

    public String getAwardLevel() {
        return awardLevel;
    }

    public void setAwardLevel(String awardLevel) {
        this.awardLevel = awardLevel;
    }

    public java.util.Date getAwardTime() {
        return awardTime;
    }

    public void setAwardTime(java.util.Date awardTime) {
        this.awardTime = awardTime;
    }

    public java.util.Date getCompetitionTime() {
        return competitionTime;
    }

    public void setCompetitionTime(java.util.Date competitionTime) {
        this.competitionTime = competitionTime;
    }

    @Override
    public String toString() {
        return "ProjectCompetitionVo{" +
                "projectId=" + projectId +
                ", competitionName='" + competitionName + '\'' +
                ", competitionLevel='" + competitionLevel + '\'' +
                ", awardLevel='" + awardLevel + '\'' +
                ", awardTime=" + awardTime +
                ", competitionTime=" + competitionTime +
                '}';
    }
}
