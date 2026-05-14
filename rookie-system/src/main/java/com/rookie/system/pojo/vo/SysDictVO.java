package com.rookie.system.pojo.vo;

import java.util.Date;

public class SysDictVO {

    private Long dictId;

    private String dictName;

    private String dictKey;

    private Integer status;

    private String remake;

    private Date createTime;

    public SysDictVO() {
    }

    public SysDictVO(Long dictId, String dictName, String dictKey, Integer status, String remake, Date createTime) {
        this.dictId = dictId;
        this.dictName = dictName;
        this.dictKey = dictKey;
        this.status = status;
        this.remake = remake;
        this.createTime = createTime;
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

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "SysDictVO{" +
                "dictId=" + dictId +
                ", dictName='" + dictName + '\'' +
                ", dictKey='" + dictKey + '\'' +
                ", status=" + status +
                ", remake='" + remake + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
