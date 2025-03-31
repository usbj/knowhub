package com.rookie.system.pojo.quarry;

import java.util.Date;

public class RoleQuarry {

    private String roleName;

    private Integer status;

    private Date beginTime;

    private Date endTime;

    public RoleQuarry() {
    }

    public RoleQuarry(String roleName, Integer status, Date beginTime, Date endTime) {
        this.roleName = roleName;
        this.status = status;
        this.beginTime = beginTime;
        this.endTime = endTime;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Date beginTime) {
        this.beginTime = beginTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "RoleQuarry{" +
                "roleName='" + roleName + '\'' +
                ", status=" + status +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}
