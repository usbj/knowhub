package com.rookie.common.pojo.entity;

import java.util.Date;

/**
 * 操作日志实体（对应 sys_oper_log）
 * 日志只追加、不可改，故不继承 BaseEntity，使用 oper_name/oper_time 作为审计列
 */
public class SysOperLog {

    /** 日志主键 */
    private Long operId;

    /** 模块标题（@Log 的 title） */
    private String title;

    /** 业务类型：OTHER/INSERT/UPDATE/DELETE/GRANT/EXPORT/IMPORT/CLEAN */
    private String businessType;

    /** 方法名（类名.方法名） */
    private String method;

    /** 请求方式 GET/POST/PUT/DELETE */
    private String requestMethod;

    /** 操作人员（用户名） */
    private String operName;

    /** 请求URL */
    private String operUrl;

    /** 操作主机IP */
    private String operIp;

    /** 操作系统（UA解析） */
    private String operOs;

    /** 浏览器（UA解析） */
    private String operBrowser;

    /** 设备类型：PC/MOBILE/TABLET/UNKNOWN */
    private String deviceType;

    /** 请求参数（JSON） */
    private String operParam;

    /** 返回结果（JSON，失败时可留空） */
    private String jsonResult;

    /** 操作状态：0正常 1异常 */
    private Integer status;

    /** 操作时间 */
    private Date operTime;

    /** 耗时（毫秒） */
    private Long costTime;

    public SysOperLog() {
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

    @Override
    public String toString() {
        return "SysOperLog{" +
                "operId=" + operId +
                ", title='" + title + '\'' +
                ", businessType='" + businessType + '\'' +
                ", method='" + method + '\'' +
                ", operName='" + operName + '\'' +
                ", status=" + status +
                ", costTime=" + costTime +
                '}';
    }
}
