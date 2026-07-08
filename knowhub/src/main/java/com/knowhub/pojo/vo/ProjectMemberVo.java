package com.knowhub.pojo.vo;

import java.util.Date;

/**
 * 项目成员对外 VO，供成员管理接口入参/出参。
 * 时间字段一律用 java.util.Date（不要用 String），序列化由全局 jackson.date-format 统一格式化。
 * nickname/username 由后端 join sys_user 带出，供前端直接展示成员昵称与账号。
 * memberRole: LEADER/MENTOR/MEMBER（字典 project_member_role）；
 * canView/canDownload/canEdit 为项目内权限标志位（单项目生效，与他项目无关，不分等级）。
 */
public class ProjectMemberVo {

    private Long memberId;

    private Long projectId;

    private Long userId;

    /** 成员角色：LEADER/MENTOR/MEMBER（字典 project_member_role） */
    private String memberRole;

    private Integer canView;

    private Integer canDownload;

    private Integer canEdit;

    /** 成员昵称（join sys_user 带出） */
    private String nickname;

    /** 成员用户名（join sys_user 带出，便于直读账号） */
    private String username;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public ProjectMemberVo() {
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMemberRole() {
        return memberRole;
    }

    public void setMemberRole(String memberRole) {
        this.memberRole = memberRole;
    }

    public Integer getCanView() {
        return canView;
    }

    public void setCanView(Integer canView) {
        this.canView = canView;
    }

    public Integer getCanDownload() {
        return canDownload;
    }

    public void setCanDownload(Integer canDownload) {
        this.canDownload = canDownload;
    }

    public Integer getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Integer canEdit) {
        this.canEdit = canEdit;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "ProjectMemberVo{" +
                "memberId=" + memberId +
                ", projectId=" + projectId +
                ", userId=" + userId +
                ", memberRole='" + memberRole + '\'' +
                ", canView=" + canView +
                ", canDownload=" + canDownload +
                ", canEdit=" + canEdit +
                ", nickname='" + nickname + '\'' +
                '}';
    }
}
