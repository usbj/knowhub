package com.rookie.system.pojo.quarry;

import java.util.Date;

public class DictQuarry {

    private String dictName;

    private String dictKey;

    private Integer status;

    private Date beginTime;

    private Date endTime;

    public DictQuarry() {
    }

    public DictQuarry(String dictName, String dictKey, Integer status, Date beginTime, Date endTime) {
        this.dictName = dictName;
        this.dictKey = dictKey;
        this.status = status;
        this.beginTime = beginTime;
        this.endTime = endTime;
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
        return "DictQuarry{" +
                "dictName='" + dictName + '\'' +
                ", dictKey='" + dictKey + '\'' +
                ", status=" + status +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}
