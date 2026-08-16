package com.knowhub.pojo.project.entity;

import com.rookie.common.pojo.BaseEntity;

/**
 * 项目成员邀请实体，对应 project_invite 表。
 * 负责人发起邀请（PENDING）→ 受邀人同意/拒绝（ACCEPTED/REJECTED）。
 * accept 时由 ProjectInviteServiceImpl 调既有 projectMemberMapper.addMember 写入一行 MEMBER 成员
 * （复用 addMembersBatch 的产出语义）+ 置邀请 ACCEPTED；reject 仅置 REJECTED（留审计迹）。
 * 重复接受幂等：既有 projectMemberMapper.getMember(projectId,userId) 查重可挡成员重复入；
 * uk_project_invite(project_id,invitee_user_id,deleted) 防同一(project,invitee)多条 ACTIVE 邀请。
 * 审计列由 BaseEntity 承载；软删 deleted 独立字段。inviter_by/handle_by 存 username 快照（照全项目约定）。
 * 非表字段：projectTitle（join project 带出）、inviterNickname（join sys_user 邀请人昵称）—— 供列表通知文案。
 */
public class ProjectInvite extends BaseEntity {

    private Long inviteId;

    private Long projectId;

    private Long inviteeUserId;

    /** 邀请状态：PENDING/ACCEPTED/REJECTED（见 ProjectInviteStatus 枚举，不入字典） */
    private String status;

    /** 发起邀请者 username 快照（负责人 LEADER/admin） */
    private String inviterBy;

    private java.util.Date inviteTime;

    /** 受邀人处理.username 快照 */
    private String handleBy;

    private java.util.Date handleTime;

    private Integer deleted;

    // ---- 非表字段（列表 join 带出） ----
    /** 项目标题（join project 带出，供列表展示与通知文案） */
    private String projectTitle;

    /** 邀请人昵称（join sys_user on inviter_by 对应 user_id 带出；此处直接 join 项目 author_id 的 user） */
    private String inviterNickname;

    public ProjectInvite() {
    }

    public Long getInviteId() {
        return inviteId;
    }

    public void setInviteId(Long inviteId) {
        this.inviteId = inviteId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getInviteeUserId() {
        return inviteeUserId;
    }

    public void setInviteeUserId(Long inviteeUserId) {
        this.inviteeUserId = inviteeUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInviterBy() {
        return inviterBy;
    }

    public void setInviterBy(String inviterBy) {
        this.inviterBy = inviterBy;
    }

    public java.util.Date getInviteTime() {
        return inviteTime;
    }

    public void setInviteTime(java.util.Date inviteTime) {
        this.inviteTime = inviteTime;
    }

    public String getHandleBy() {
        return handleBy;
    }

    public void setHandleBy(String handleBy) {
        this.handleBy = handleBy;
    }

    public java.util.Date getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(java.util.Date handleTime) {
        this.handleTime = handleTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public String getInviterNickname() {
        return inviterNickname;
    }

    public void setInviterNickname(String inviterNickname) {
        this.inviterNickname = inviterNickname;
    }
}