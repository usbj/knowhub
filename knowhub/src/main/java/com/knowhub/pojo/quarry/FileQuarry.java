package com.knowhub.pojo.quarry;

import java.util.Date;

/**
 * 文件对象列表查询条件，作为 Mapper parameterType 与列表接口入参。
 * 由 query string 绑定（无 @RequestBody）；pageNum/pageSize 由 PageUtil 从请求读取。
 */
public class FileQuarry {

    /** 业务类型过滤 */
    private String businessType;

    /** 上传状态过滤（PENDING/CONFIRMED/FAILED/GC） */
    private String uploadStatus;

    /** 访问语义过滤（PUBLIC/PRIVATE） */
    private String access;

    /** 上传人过滤 */
    private String createBy;

    private Date beginTime;

    private Date endTime;

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getAccess() {
        return access;
    }

    public void setAccess(String access) {
        this.access = access;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
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
        return "FileQuarry{" +
                "businessType='" + businessType + '\'' +
                ", uploadStatus='" + uploadStatus + '\'' +
                ", access='" + access + '\'' +
                ", createBy='" + createBy + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}
