package com.rookie.system.pojo.quarry;

import java.util.Date;

public class MenuQuarry {
    private String MenuName;

    private String permKey;

    private Integer status;

    private Date beginTime;

    private Date endTime;

    public MenuQuarry() {
    }

    public MenuQuarry(String menuName, String permKey, Integer status, Date beginTime, Date endTime) {
        MenuName = menuName;
        this.permKey = permKey;
        this.status = status;
        this.beginTime = beginTime;
        this.endTime = endTime;
    }

    public String getMenuName() {
        return MenuName;
    }

    public void setMenuName(String menuName) {
        MenuName = menuName;
    }

    public String getPermKey() {
        return permKey;
    }

    public void setPermKey(String permKey) {
        this.permKey = permKey;
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
}
