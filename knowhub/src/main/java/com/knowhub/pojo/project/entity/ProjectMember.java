package com.knowhub.pojo.project.entity;

import com.rookie.common.pojo.BaseEntity;

/**
 * 项目成员实体，对应 project_member 表（团队名单 + 项目内权限标志位）。
 * memberRole: LEADER负责人/MENTOR导师/MEMBER参与者（见 ProjectMemberRole 枚举）。
 * 项目内权限标志位 canView/canDownload/canEdit：单项目生效，与他项目无关，不分等级。
 * LEADER 判定时强制全权不看标志位；每项目仅一个 LEADER（service 层事务校验唯一性）。
 * 角色默认标志位（service 层创建成员时按角色给默认值，可微调）：
 *   LEADER→1/1/1、MENTOR→1/1/0、MEMBER→1/0/0。
 * 审计列由 BaseEntity 承载；软删 deleted 独立字段。
 */
public class ProjectMember extends BaseEntity {

    private Long memberId;

    private Long projectId;

    private Long userId;

    /** 成员角色：LEADER/MENTOR/MEMBER（见 ProjectMemberRole 枚举） */
    private String memberRole;

    private Integer canView;

    private Integer canDownload;

    private Integer canEdit;

    private Integer deleted;

    // ---- 非表字段（成员列表 join sys_user 带出，非表字段） ----
    /** 成员昵称（join sys_user on user_id 带出，供前端直接展示） */
    private String nickname;

    /** 成员用户名（join sys_user 带出，便于直读账号） */
    private String username;

    public ProjectMember() {
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
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

    @Override
    public String toString() {
        return "ProjectMember{" +
                "memberId=" + memberId +
                ", projectId=" + projectId +
                ", userId=" + userId +
                ", memberRole='" + memberRole + '\'' +
                ", canView=" + canView +
                ", canDownload=" + canDownload +
                ", canEdit=" + canEdit +
                ", deleted=" + deleted +
                ", nickname='" + nickname + '\'' +
                '}';
    }
}
