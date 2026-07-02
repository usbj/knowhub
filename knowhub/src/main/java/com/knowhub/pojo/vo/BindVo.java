package com.knowhub.pojo.vo;

/**
 * 文件业务关联绑定入参。业务行创建后回填 file_object.biz_ref_id，
 * 便于"删业务行时级联清文件"。PUT /file/bind 携带。
 */
public class BindVo {

    private Long objectId;

    private Long bizRefId;

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public Long getBizRefId() {
        return bizRefId;
    }

    public void setBizRefId(Long bizRefId) {
        this.bizRefId = bizRefId;
    }

    @Override
    public String toString() {
        return "BindVo{" +
                "objectId=" + objectId +
                ", bizRefId=" + bizRefId +
                '}';
    }
}
