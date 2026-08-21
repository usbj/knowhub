package com.knowhub.pojo.storage.entity;

import com.rookie.common.pojo.BaseEntity;

/**
 * 文件迁移任务实体，对应 file_migration_task 表。
 * <p>
 * 记录一次数据迁移（源 OSS → 目标 OSS，或 本地 → 目标 OSS）的连接元信息与进度。
 * 敏感凭证（accessKey/secretKey）不在表里存明文：迁移请求由管理员后台填连接参数（含凭证），
 * 后端内存用完即弃，表只存 endpoint/bucket/进度/状态/错误信息。
 * <p>
 * source_type 标识源端类型：OSS（源 OSS→目标 OSS，source_* 列存源 OSS 连接元信息）/
 * LOCAL（本地→目标 OSS，source_* 列留空，源是后端本地磁盘 storage.local.base-path）。
 * status 状态机：PENDING 建任务待跑 → RUNNING @Async 线程拷贝中 → SUCCESS/FAILED 拷贝结束 → CANCELED 取消。
 * total_count 源端对象总数（OSS 走 listObjectsV2 全量计，本地走 Files.walk 计）；done_count 已成功拷贝数；failed_count 拷贝失败数。
 * 目录结构一致：源 objectKey 原样作目标 objectKey。
 * 审计列由 BaseEntity 承载。
 */
public class FileMigrationTask extends BaseEntity {

    private Long taskId;

    /** 源端类型：OSS / LOCAL（见 MigrationSourceType 常量，OSS→OSS 用 OSS，本地→OSS 用 LOCAL） */
    private String sourceType;

    /** 源 OSS endpoint（连接元信息，非凭证；source_type=LOCAL 时留空） */
    private String sourceEndpoint;

    /** 源 OSS region（可空，RustFS 不校验） */
    private String sourceRegion;

    /** 源 OSS 桶名 */
    private String sourceBucket;

    /** 源 OSS 是否 path-style（RustFS 默认 true） */
    private Boolean sourcePathStyleAccess;

    /** 目标 OSS endpoint */
    private String targetEndpoint;

    /** 目标 OSS region */
    private String targetRegion;

    /** 目标 OSS 桶名 */
    private String targetBucket;

    /** 目标 OSS 是否 path-style */
    private Boolean targetPathStyleAccess;

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELED */
    private String status;

    /** 源桶对象总数（listObjectsV2 全量计） */
    private Long totalCount;

    /** 已成功拷贝数 */
    private Long doneCount;

    /** 拷贝失败数 */
    private Long failedCount;

    /** 错误信息（FAILED 时填） */
    private String errorMessage;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceEndpoint() {
        return sourceEndpoint;
    }

    public void setSourceEndpoint(String sourceEndpoint) {
        this.sourceEndpoint = sourceEndpoint;
    }

    public String getSourceRegion() {
        return sourceRegion;
    }

    public void setSourceRegion(String sourceRegion) {
        this.sourceRegion = sourceRegion;
    }

    public String getSourceBucket() {
        return sourceBucket;
    }

    public void setSourceBucket(String sourceBucket) {
        this.sourceBucket = sourceBucket;
    }

    public Boolean getSourcePathStyleAccess() {
        return sourcePathStyleAccess;
    }

    public void setSourcePathStyleAccess(Boolean sourcePathStyleAccess) {
        this.sourcePathStyleAccess = sourcePathStyleAccess;
    }

    public String getTargetEndpoint() {
        return targetEndpoint;
    }

    public void setTargetEndpoint(String targetEndpoint) {
        this.targetEndpoint = targetEndpoint;
    }

    public String getTargetRegion() {
        return targetRegion;
    }

    public void setTargetRegion(String targetRegion) {
        this.targetRegion = targetRegion;
    }

    public String getTargetBucket() {
        return targetBucket;
    }

    public void setTargetBucket(String targetBucket) {
        this.targetBucket = targetBucket;
    }

    public Boolean getTargetPathStyleAccess() {
        return targetPathStyleAccess;
    }

    public void setTargetPathStyleAccess(Boolean targetPathStyleAccess) {
        this.targetPathStyleAccess = targetPathStyleAccess;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(Long doneCount) {
        this.doneCount = doneCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Long failedCount) {
        this.failedCount = failedCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
