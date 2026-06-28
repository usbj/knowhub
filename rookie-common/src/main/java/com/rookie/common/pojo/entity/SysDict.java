package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

public class SysDict extends BaseEntity {

    private Long dictId;

    private String dictName;

    private String dictKey;

    private Integer status;

    private String remake;

    public SysDict() {
    }

    public SysDict(Date createTime, Date updateTime, String createBy, String updateBy, Long dictId, String dictName, String dictKey, Integer status, String remake) {
        super(createTime, updateTime, createBy, updateBy);
        this.dictId = dictId;
        this.dictName = dictName;
        this.dictKey = dictKey;
        this.status = status;
        this.remake = remake;
    }

    public Long getDictId() {
        return dictId;
    }

    public void setDictId(Long dictId) {
        this.dictId = dictId;
    }

    public String getDictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
    }

    public String getDictKey() {
        return dictKey;
    }

    public void setDictKey(String dictKey) {
        this.dictKey = dictKey;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemake() {
        return remake;
    }

    public void setRemake(String remake) {
        this.remake = remake;
    }

    @Override
    public String toString() {
        return "SysDict{" +
                "dictId=" + dictId +
                ", dictName='" + dictName + '\'' +
                ", dictKey='" + dictKey + '\'' +
                ", status=" + status +
                ", remake='" + remake + '\'' +
                ", createTime=" + getCreateTime() +
                ", updateTime=" + getUpdateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                '}';
    }
}
