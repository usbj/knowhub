package com.rookie.common.pojo.entity;

import java.util.Date;

/**
 * 错误日志实体（对应 sys_error_log）
 * 只记"来源 + 关联 + 异常本身 + 时间 + 操作人"；HTTP 环境信息归操作日志，此处不重复
 * 不继承 BaseEntity：日志只追加
 */
public class SysErrorLog {

    /** 错误日志主键 */
    private Long errorId;

    /** 错误来源：REQUEST/SCHEDULED/ASYNC/EVENT/INIT/OTHER */
    private String sourceType;

    /** 关联操作日志ID（仅REQUEST来源且接口带@Log时可能有值，可为空） */
    private Long operLogId;

    /** 错误简述：请求来源填URL，定时任务填任务名，异步任务填方法名等 */
    private String title;

    /** 操作人员（请求来源且有登录态时填；其他来源可为空） */
    private String operName;

    /** 异常类全名 */
    private String exceptionType;

    /** 异常消息 */
    private String exceptionMsg;

    /** 完整堆栈 */
    private String exceptionStack;

    /** 错误时间 */
    private Date errorTime;

    public SysErrorLog() {
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

    @Override
    public String toString() {
        return "SysErrorLog{" +
                "errorId=" + errorId +
                ", sourceType='" + sourceType + '\'' +
                ", operLogId=" + operLogId +
                ", title='" + title + '\'' +
                ", exceptionType='" + exceptionType + '\'' +
                ", errorTime=" + errorTime +
                '}';
    }
}
