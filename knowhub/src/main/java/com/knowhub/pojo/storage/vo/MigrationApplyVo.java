package com.knowhub.pojo.storage.vo;

/**
 * 数据迁移申请入参。前端填源类型 + 目标 OSS 连接参数（OSS→OSS 还需源 OSS 连接参数）提交，
 * 后端建任务行后 @Async 线程拷贝。
 * <p>
 * sourceType：OSS（源 OSS→目标 OSS，需 source* 参数）/ LOCAL（本地→目标 OSS，source* 留空，源是后端本地磁盘）。
 * 凭证（accessKey/secretKey）内存用完即弃、不落库；表只存 endpoint/bucket/进度/状态。
 * region 可空（RustFS 不校验，SDK 要求非空时后端兜默认占位）。
 * pathStyleAccess 可空，默认 true（RustFS 默认 path-style）。
 */
public class MigrationApplyVo {

    /** 源端类型：OSS / LOCAL（LOCAL 时 source* 参数忽略，源是后端本地磁盘） */
    private String sourceType;

    // ---- 源 OSS（sourceType=OSS 时必填，=LOCAL 时留空） ----
    private String sourceEndpoint;
    private String sourceRegion;
    private String sourceAccessKey;
    private String sourceSecretKey;
    private String sourceBucket;
    private Boolean sourcePathStyleAccess;

    // ---- 目标 OSS ----
    private String targetEndpoint;
    private String targetRegion;
    private String targetAccessKey;
    private String targetSecretKey;
    private String targetBucket;
    private Boolean targetPathStyleAccess;

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

    public String getSourceAccessKey() {
        return sourceAccessKey;
    }

    public void setSourceAccessKey(String sourceAccessKey) {
        this.sourceAccessKey = sourceAccessKey;
    }

    public String getSourceSecretKey() {
        return sourceSecretKey;
    }

    public void setSourceSecretKey(String sourceSecretKey) {
        this.sourceSecretKey = sourceSecretKey;
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

    public String getTargetAccessKey() {
        return targetAccessKey;
    }

    public void setTargetAccessKey(String targetAccessKey) {
        this.targetAccessKey = targetAccessKey;
    }

    public String getTargetSecretKey() {
        return targetSecretKey;
    }

    public void setTargetSecretKey(String targetSecretKey) {
        this.targetSecretKey = targetSecretKey;
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
}
