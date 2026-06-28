package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

public class SysDictData extends BaseEntity {

    Long dictDataId;

    Long dictId;

    String dictKey;

    String dictDataLabel;

    String dictDataValue;

    String remark;

    String dictDataSort;

    String tagType;

    String tagEffect;

    String cssClass;

    String extJson;

    String isDefault;

    public SysDictData() {
    }

    public SysDictData(Date createTime, Date updateTime, String createBy, String updateBy, Long dictDataId, Long dictId, String dictKey, String dictDataLabel, String dictDataValue, String remark, String dictDataSort, String tagType, String tagEffect, String cssClass, String extJson, String isDefault) {
        super(createTime, updateTime, createBy, updateBy);
        this.dictDataId = dictDataId;
        this.dictId = dictId;
        this.dictKey = dictKey;
        this.dictDataLabel = dictDataLabel;
        this.dictDataValue = dictDataValue;
        this.remark = remark;
        this.dictDataSort = dictDataSort;
        this.tagType = tagType;
        this.tagEffect = tagEffect;
        this.cssClass = cssClass;
        this.extJson = extJson;
        this.isDefault = isDefault;
    }

    public String getDictDataLabel() {
        return dictDataLabel;
    }

    public void setDictDataLabel(String dictDataLabel) {
        this.dictDataLabel = dictDataLabel;
    }

    public Long getDictDataId() {
        return dictDataId;
    }

    public void setDictDataId(Long dictDataId) {
        this.dictDataId = dictDataId;
    }

    public Long getDictId() {
        return dictId;
    }

    public void setDictId(Long dictId) {
        this.dictId = dictId;
    }

    public String getDictKey() {
        return dictKey;
    }

    public void setDictKey(String dictKey) {
        this.dictKey = dictKey;
    }

    public String getDictDataValue() {
        return dictDataValue;
    }

    public void setDictDataValue(String dictDataValue) {
        this.dictDataValue = dictDataValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getDictDataSort() {
        return dictDataSort;
    }

    public void setDictDataSort(String dictDataSort) {
        this.dictDataSort = dictDataSort;
    }

    public String getTagType() {
        return tagType;
    }

    public void setTagType(String tagType) {
        this.tagType = tagType;
    }

    public String getTagEffect() {
        return tagEffect;
    }

    public void setTagEffect(String tagEffect) {
        this.tagEffect = tagEffect;
    }

    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    public String getExtJson() {
        return extJson;
    }

    public void setExtJson(String extJson) {
        this.extJson = extJson;
    }

    public String getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(String isDefault) {
        this.isDefault = isDefault;
    }
}
