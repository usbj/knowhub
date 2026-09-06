package com.knowhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储连接参数与阈值，绑定 application.yml 的 storage: 段。
 * 敏感值（access-key/secret-key）由 ${ENV:默认} 占位，真实值环境变量注入，仓库不落明文。
 * 类型白名单、各业务体积上限走字典（可后台改），收口到 StorageConfigReader，不在此类。
 */
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** RustFS S3 兼容端点，末尾不带斜杠 */
    private String endpoint;

    /** region 任意占位值（RustFS 不校验，SDK 要求非空） */
    private String region;

    private String accessKey;

    private String secretKey;

    /** 桶名 */
    private String bucket;

    /** path-style 必须 true（RustFS 默认 path-style） */
    private boolean pathStyleAccess = true;

    /** 上传令牌预签名有效期（分钟） */
    private long uploadExpireMinutes = 10;

    /** 下载预签名有效期（分钟） */
    private long downloadExpireMinutes = 5;

    /** PENDING 未确认 GC 阈值（分钟） */
    private long pendingTtlMinutes = 30;

    /** GC 定时扫描间隔（分钟） */
    private long gcIntervalMinutes = 10;

    /** PUBLIC 对象是否直拼公开读 URL（false 则也走预签名 GET） */
    private boolean publicBucketReadable = true;

    /** 本地存储模式根目录（access_mode=local 时文件落盘于此，objectKey 即相对路径，默认 ./knowhub-upload） */
    private String localBasePath = "./knowhub-upload";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public long getUploadExpireMinutes() {
        return uploadExpireMinutes;
    }

    public void setUploadExpireMinutes(long uploadExpireMinutes) {
        this.uploadExpireMinutes = uploadExpireMinutes;
    }

    public long getDownloadExpireMinutes() {
        return downloadExpireMinutes;
    }

    public void setDownloadExpireMinutes(long downloadExpireMinutes) {
        this.downloadExpireMinutes = downloadExpireMinutes;
    }

    public long getPendingTtlMinutes() {
        return pendingTtlMinutes;
    }

    public void setPendingTtlMinutes(long pendingTtlMinutes) {
        this.pendingTtlMinutes = pendingTtlMinutes;
    }

    public long getGcIntervalMinutes() {
        return gcIntervalMinutes;
    }

    public void setGcIntervalMinutes(long gcIntervalMinutes) {
        this.gcIntervalMinutes = gcIntervalMinutes;
    }

    public boolean isPublicBucketReadable() {
        return publicBucketReadable;
    }

    public void setPublicBucketReadable(boolean publicBucketReadable) {
        this.publicBucketReadable = publicBucketReadable;
    }

    public String getLocalBasePath() {
        return localBasePath;
    }

    public void setLocalBasePath(String localBasePath) {
        this.localBasePath = localBasePath;
    }
}
