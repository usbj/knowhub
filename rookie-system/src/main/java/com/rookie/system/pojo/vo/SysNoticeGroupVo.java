package com.rookie.system.pojo.vo;

import com.rookie.system.pojo.SysNoticeGroupMember;

import java.util.List;

public class SysNoticeGroupVo {

    private Long groupId;

    private String groupName;

    private String groupCode;

    private String groupDesc;

    private Integer status;

    private String createTime;

    private List<SysNoticeGroupMember> members;

    public SysNoticeGroupVo() {
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getGroupDesc() {
        return groupDesc;
    }

    public void setGroupDesc(String groupDesc) {
        this.groupDesc = groupDesc;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public List<SysNoticeGroupMember> getMembers() {
        return members;
    }

    public void setMembers(List<SysNoticeGroupMember> members) {
        this.members = members;
    }
}
