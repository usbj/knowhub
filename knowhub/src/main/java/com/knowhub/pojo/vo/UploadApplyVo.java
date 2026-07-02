package com.knowhub.pojo.vo;

/**
 * 上传令牌申请入参。前端预签名直传前先调 POST /file/upload-token 携带本 VO，
 * 后端校验 contentType/size 落白名单后签发 PutObject 预签名 URL。
 */
public class UploadApplyVo {

    /** 业务类型（FileBusinessType 枚举 code，决定 objectKey 前缀/白名单/默认 access） */
    private String businessType;

    /** 声明的 MIME 类型，须落在该业务类型的类型白名单内 */
    private String contentType;

    /** 声明的字节数，须 ≤ 该业务类型的体积上限 */
    private Long size;

    /** 原始文件名（仅展示用，不参与 objectKey） */
    private String originalName;

    /** 访问语义；缺省按 businessType 默认值（BLOG_* → PUBLIC，其余 → PRIVATE） */
    private String access;

    /** 业务关联 ID；可空，上传时业务行可能还没建，确认/绑定后回填 */
    private Long bizRefId;

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getAccess() {
        return access;
    }

    public void setAccess(String access) {
        this.access = access;
    }

    public Long getBizRefId() {
        return bizRefId;
    }

    public void setBizRefId(Long bizRefId) {
        this.bizRefId = bizRefId;
    }

    @Override
    public String toString() {
        return "UploadApplyVo{" +
                "businessType='" + businessType + '\'' +
                ", contentType='" + contentType + '\'' +
                ", size=" + size +
                ", originalName='" + originalName + '\'' +
                ", access='" + access + '\'' +
                ", bizRefId=" + bizRefId +
                '}';
    }
}
