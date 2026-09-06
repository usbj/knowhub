package com.knowhub.pojo.storage.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 文件对象元数据实体，对应 file_object 表。
 * 只存对象元数据（bucket/objectKey/大小/类型/校验值/状态），对象本体在 RustFS，
 * 元数据与对象解耦。审计列由 BaseEntity 承载；软删 deleted 独立字段（仅标记元数据，
 * 对象本体由 GC 定时任务异步 DeleteObject，见 doc/storage/file-storage-module-design.md §3.4）。
 */
public class FileObject extends BaseEntity {

    private Long objectId;

    private String bucket;

    private String objectKey;

    private String originalName;

    private Long contentLength;

    private String contentType;

    private String checksum;

    /** 业务类型：BLOG_COVER/BLOG_BODY/PROJECT_SRC/...（见 FileBusinessType 枚举） */
    private String businessType;

    /** 业务关联 ID（可空：上传令牌签发时业务行可能还没建，确认/绑定后回填） */
    private Long bizRefId;

    /** 访问语义：PUBLIC 公开 / PRIVATE 私有（见 FileAccess 枚举） */
    private String access;

    /** 上传状态：PENDING 待确认 / CONFIRMED 已确认 / FAILED 失败 / GC 待回收（见 UploadStatus 枚举） */
    private String uploadStatus;

    private Integer deleted;

    public FileObject() {
    }

    public FileObject(Date createTime, Date updateTime, String createBy, String updateBy,
                      Long objectId, String bucket, String objectKey, String originalName,
                      Long contentLength, String contentType, String checksum,
                      String businessType, Long bizRefId, String access, String uploadStatus,
                      Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.objectId = objectId;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.checksum = checksum;
        this.businessType = businessType;
        this.bizRefId = bizRefId;
        this.access = access;
        this.uploadStatus = uploadStatus;
        this.deleted = deleted;
    }

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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "FileObject{" +
                "objectId=" + objectId +
                ", bucket='" + bucket + '\'' +
                ", objectKey='" + objectKey + '\'' +
                ", originalName='" + originalName + '\'' +
                ", contentLength=" + contentLength +
                ", contentType='" + contentType + '\'' +
                ", businessType='" + businessType + '\'' +
                ", bizRefId=" + bizRefId +
                ", access='" + access + '\'' +
                ", uploadStatus='" + uploadStatus + '\'' +
                ", deleted=" + deleted +
                ", createTime=" + getCreateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                '}';
    }
}
