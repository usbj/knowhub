package com.rookie.system.pojo.quarry;

public class NoticeGroupQuarry {

    private String groupName;

    private String groupCode;

    private Integer status;

    public NoticeGroupQuarry() {
    }

    public NoticeGroupQuarry(String groupName, String groupCode, Integer status) {
        this.groupName = groupName;
        this.groupCode = groupCode;
        this.status = status;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
