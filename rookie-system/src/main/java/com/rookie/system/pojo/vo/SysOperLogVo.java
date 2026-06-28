package com.rookie.system.pojo.vo;

import java.util.Date;

/**
 * 操作日志展示对象
 * 在 SysOperLog 基础上增加 errorLogId：失败操作关联的错误日志主键，供前端"错误日志"按钮跳转
 */
public class SysOperLogVo {

    private Long operId;

    private String title;

    private String businessType;

    private String method;

    private String requestMethod;

    private String operName;

    private String operUrl;

    private String operIp;

    private String operOs;

    private String operBrowser;

    private String deviceType;

    private String operParam;

    private String jsonResult;

    private Integer status;

    private Date operTime;

    private Long costTime;

    /** 关联的错误日志主键（仅 status=1 且存在错误日志时有值，可为空） */
    private Long errorLogId;

    public SysOperLogVo() {
    }

    public Long getOperId() {
        return operId;
    }

    public void setOperId(Long operId) {
        this.operId = operId;
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

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getOperName() {
        return operName;
    }

    public void setOperName(String operName) {
        this.operName = operName;
    }

    public String getOperUrl() {
        return operUrl;
    }

    public void setOperUrl(String operUrl) {
        this.operUrl = operUrl;
    }

    public String getOperIp() {
        return operIp;
    }

    public void setOperIp(String operIp) {
        this.operIp = operIp;
    }

    public String getOperOs() {
        return operOs;
    }

    public void setOperOs(String operOs) {
        this.operOs = operOs;
    }

    public String getOperBrowser() {
        return operBrowser;
    }

    public void setOperBrowser(String operBrowser) {
        this.operBrowser = operBrowser;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getOperParam() {
        return operParam;
    }

    public void setOperParam(String operParam) {
        this.operParam = operParam;
    }

    public String getJsonResult() {
        return jsonResult;
    }

    public void setJsonResult(String jsonResult) {
        this.jsonResult = jsonResult;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getOperTime() {
        return operTime;
    }

    public void setOperTime(Date operTime) {
        this.operTime = operTime;
    }

    public Long getCostTime() {
        return costTime;
    }

    public void setCostTime(Long costTime) {
        this.costTime = costTime;
    }

    public Long getErrorLogId() {
        return errorLogId;
    }

    public void setErrorLogId(Long errorLogId) {
        this.errorLogId = errorLogId;
    }
}
