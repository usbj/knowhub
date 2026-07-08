package com.knowhub.pojo.vo;

import java.util.Date;

/**
 * 文件对象元数据回显 VO。列表/详情接口出参。
 * 时间字段一律用 java.util.Date（不要用 String），序列化由全局 jackson.date-format 统一格式化。
 */
public class FileObjectVo {

    private Long objectId;

    private String bucket;

    private String objectKey;

    private String originalName;

    private Long contentLength;

    private String contentType;

    private String checksum;

    private String businessType;

    private Long bizRefId;

    private String access;

    private String uploadStatus;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public Long getBizRefId() {
        return bizRefId;
    }

    public void setBizRefId(Long bizRefId) {
        this.bizRefId = bizRefId;
    }

    public String getAccess() {
        return access;
    }

    public void setAccess(String access) {
        this.access = access;
    }

    public String getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "FileObjectVo{" +
                "objectId=" + objectId +
                ", objectKey='" + objectKey + '\'' +
                ", originalName='" + originalName + '\'' +
                ", contentLength=" + contentLength +
                ", contentType='" + contentType + '\'' +
                ", businessType='" + businessType + '\'' +
                ", bizRefId=" + bizRefId +
                ", access='" + access + '\'' +
                ", uploadStatus='" + uploadStatus + '\'' +
                '}';
    }
}
