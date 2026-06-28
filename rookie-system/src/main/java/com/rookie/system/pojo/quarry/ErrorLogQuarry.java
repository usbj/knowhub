package com.rookie.system.pojo.quarry;

import java.util.Date;

/**
 * 错误日志查询条件
 */
public class ErrorLogQuarry {

    /** 错误来源 */
    private String sourceType;

    /** 错误简述（模糊） */
    private String title;

    /** 操作人员（模糊） */
    private String operName;

    /** 异常类型（模糊） */
    private String exceptionType;

    /** 起始时间 */
    private Date beginTime;

    /** 结束时间 */
    private Date endTime;

    public ErrorLogQuarry() {
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOperName() {
        return operName;
    }

    public void setOperName(String operName) {
        this.operName = operName;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
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
        return "ErrorLogQuarry{" +
                "sourceType='" + sourceType + '\'' +
                ", title='" + title + '\'' +
                ", operName='" + operName + '\'' +
                ", exceptionType='" + exceptionType + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}
