package com.knowhub.pojo.entity;

/**
 * 比赛项目子表实体，对应 project_competition 表（1:1，主键兼外键）。
 * 只存比赛特有字段；团队名单由 project_member 承载，不在此重复。
 * 不继承 BaseEntity、不软删——随主表（主表删则一并清理），无独立审计列。
 * PRACTICE/OPS 暂不做（后续加子表 + 补 project_type 字典 + 前端表单配置，主表不动）。
 */
public class ProjectCompetition {

    /** FK→project（主键兼外键） */
    private Long projectId;

    private String competitionName;

    /** 比赛级别：校级/省级/国家级/国际级 */
    private String competitionLevel;

    /** 获奖等级：特等/一等/二等/三等/优秀/无 */
    private String awardLevel;

    private java.util.Date awardTime;

    private java.util.Date competitionTime;

    public ProjectCompetition() {
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
        return "ProjectCompetition{" +
                "projectId=" + projectId +
                ", competitionName='" + competitionName + '\'' +
                ", competitionLevel='" + competitionLevel + '\'' +
                ", awardLevel='" + awardLevel + '\'' +
                ", awardTime=" + awardTime +
                ", competitionTime=" + competitionTime +
                '}';
    }
}
