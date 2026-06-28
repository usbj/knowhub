package com.rookie.system.pojo;

public class SysNoticeGroupMember {

    private Long id;

    private Long groupId;

    private Long userId;

    /**
     * 以下展示字段不对应 sys_notice_group_member 表列，
     * 由 getSysNoticeGroupMemberByGroupId 关联 sys_user 查询时填充，
     * 仅供前端成员表格展示，新增/移除成员时不使用。
     */
    private String username;

    private String nickName;

    private String phoneNumber;

    private Integer status;

    public SysNoticeGroupMember() {
    }

    public SysNoticeGroupMember(Long id, Long groupId, Long userId) {
        this.id = id;
        this.groupId = groupId;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
