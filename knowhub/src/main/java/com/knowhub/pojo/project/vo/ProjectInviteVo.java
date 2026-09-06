package com.knowhub.pojo.project.vo;

import java.util.Date;

/**
 * 项目邀请展示 VO（前台「我的协作」页「我收到的项目邀请」tab 列表用）。
 * 字段口径：照 ProjectInvite 实体，脱去审计列（createTime/updateTime/createBy/updateBy）
 * 与 deleted（前台不感知软删）；join 字段 projectTitle/inviterNickname 保留供前端展示。
 * inviterBy 存邀请人 username 快照（项目作者/负责人），列表既能直读账号又可走 inviterNickname 昵称展示。
 * status 用前端 inline 映射文案（PENDING/ACCEPTED/REJECTED 照 collaboration 页 statusMeta 口径）。
 */
public class ProjectInviteVo {

    private Long inviteId;

    private Long projectId;

    private Long inviteeUserId;

    private String status;

    private String inviterBy;

    private Date inviteTime;

    private String handleBy;

    private Date handleTime;

    // ---- join 带出 ----
    private String projectTitle;

    private String inviterNickname;

    /** 项目作者 userId（join project 带出，前端判定用，可选） */
    private Long projectAuthorId;

    public ProjectInviteVo() {
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

    public Date getInviteTime() {
        return inviteTime;
    }

    public void setInviteTime(Date inviteTime) {
        this.inviteTime = inviteTime;
    }

    public String getHandleBy() {
        return handleBy;
    }

    public void setHandleBy(String handleBy) {
        this.handleBy = handleBy;
    }

    public Date getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(Date handleTime) {
        this.handleTime = handleTime;
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

    public Long getProjectAuthorId() {
        return projectAuthorId;
    }

    public void setProjectAuthorId(Long projectAuthorId) {
        this.projectAuthorId = projectAuthorId;
    }
}