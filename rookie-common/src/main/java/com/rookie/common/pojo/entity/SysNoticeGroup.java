package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

public class SysNoticeGroup extends BaseEntity {

    private Long groupId;

    private String groupName;

    private String groupCode;

    private String groupDesc;

    private Integer status;

    public SysNoticeGroup() {
    }

    public SysNoticeGroup(Date createTime, Date updateTime, String createBy, String updateBy, Long groupId, String groupName, String groupCode, String groupDesc, Integer status) {
        super(createTime, updateTime, createBy, updateBy);
        this.groupId = groupId;
        this.groupName = groupName;
        this.groupCode = groupCode;
        this.groupDesc = groupDesc;
        this.status = status;
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

    @Override
    public String toString() {
        return "SysNoticeGroup{" +
                "groupId=" + groupId +
                ", groupName='" + groupName + '\'' +
                ", groupCode='" + groupCode + '\'' +
                ", groupDesc='" + groupDesc + '\'' +
                ", status=" + status +
                ", createTime=" + getCreateTime() +
                ", updateTime=" + getUpdateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                '}';
    }
}
