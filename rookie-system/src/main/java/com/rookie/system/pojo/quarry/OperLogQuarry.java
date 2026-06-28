package com.rookie.system.pojo.quarry;

import java.util.Date;

/**
 * 操作日志查询条件
 */
public class OperLogQuarry {

    /** 模块标题（模糊） */
    private String title;

    /** 业务类型 */
    private String businessType;

    /** 操作人员（模糊） */
    private String operName;

    /** 操作状态：0正常 1异常 */
    private Integer status;

    /** 请求方式 */
    private String requestMethod;

    /** 起始时间 */
    private Date beginTime;

    /** 结束时间 */
    private Date endTime;

    public OperLogQuarry() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getOperName() {
        return operName;
    }

    public void setOperName(String operName) {
        this.operName = operName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
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
        return "OperLogQuarry{" +
                "title='" + title + '\'' +
                ", businessType='" + businessType + '\'' +
                ", operName='" + operName + '\'' +
                ", status=" + status +
                ", requestMethod='" + requestMethod + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}
