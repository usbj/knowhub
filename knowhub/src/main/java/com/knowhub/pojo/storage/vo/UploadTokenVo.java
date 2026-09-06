package com.knowhub.pojo.storage.vo;

/**
 * 上传令牌签发结果。后端校验通过、insert file_object(PENDING) 后返回，
 * 前端拿 uploadUrl 直接 PUT 直传 RustFS，传完调 POST /file/confirm/{objectId} 确认。
 */
public class UploadTokenVo {

    /** 预签名 PUT URL，前端直传用（带 Content-Type/Content-Length 条件约束） */
    private String uploadUrl;

    /** 对象 key（业务前缀/日期/uuid.扩展名），前端回传 confirm 用不上，仅供调试 */
    private String objectKey;

    /** 元数据行主键，前端 confirm 时回传 */
    private Long objectId;

    /** 预签名有效期（秒） */
    private Long expires;

    public UploadTokenVo() {
    }

    public UploadTokenVo(String uploadUrl, String objectKey, Long objectId, Long expires) {
        this.uploadUrl = uploadUrl;
        this.objectKey = objectKey;
        this.objectId = objectId;
        this.expires = expires;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public Long getExpires() {
        return expires;
    }

    public void setExpires(Long expires) {
        this.expires = expires;
    }

    @Override
    public String toString() {
        return "UploadTokenVo{" +
                "uploadUrl='" + uploadUrl + '\'' +
                ", objectKey='" + objectKey + '\'' +
                ", objectId=" + objectId +
                ", expires=" + expires +
                '}';
    }
}
