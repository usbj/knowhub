package com.rookie.system.pojo.vo;

import java.util.Date;

/**
 * 错误日志展示对象
 * 在 SysErrorLog 基础上保留 operLogId：请求来源错误关联的操作日志主键，供前端"查看操作日志"按钮跳转
 */
public class SysErrorLogVo {

    private Long errorId;

    private String sourceType;

    private Long operLogId;

    private String title;

    private String operName;

    private String exceptionType;

    private String exceptionMsg;

    private String exceptionStack;

    private Date errorTime;

    public SysErrorLogVo() {
    }

    public Long getErrorId() {
        return errorId;
    }

    public void setErrorId(Long errorId) {
        this.errorId = errorId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getOperLogId() {
        return operLogId;
    }

    public void setOperLogId(Long operLogId) {
        this.operLogId = operLogId;
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

    public String getExceptionMsg() {
        return exceptionMsg;
    }

    public void setExceptionMsg(String exceptionMsg) {
        this.exceptionMsg = exceptionMsg;
    }

    public String getExceptionStack() {
        return exceptionStack;
    }

    public void setExceptionStack(String exceptionStack) {
        this.exceptionStack = exceptionStack;
    }

    public Date getErrorTime() {
        return errorTime;
    }

    public void setErrorTime(Date errorTime) {
        this.errorTime = errorTime;
    }
}
