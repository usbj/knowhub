package com.rookie.common.pojo;

import java.util.Date;

public class BaseEntity {

    private Date createTime;

    private Date updateTime;

    private String createBy;

    private String updateBy;

    public BaseEntity() {
    }

    public BaseEntity(Date createTime, Date updateTime, String createBy, String updateBy) {
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.createBy = createBy;
        this.updateBy = updateBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    @Override
    public String toString() {
        return "BaseEntity{" +
                "createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", createBy='" + createBy + '\'' +
                ", updateBy='" + updateBy + '\'' +
                '}';
    }
}
