package com.rookie.system.pojo.vo;

public class SysDictDataVo {

    Long dictDataId;

    Long dictId;

    String dictKey;

    String dictDataName;

    String dictDataValue;

    String remark;

    String dictDataSort;

    String createTime;

    public SysDictDataVo() {
    }

    public SysDictDataVo(Long dictDataId, Long dictId, String dictKey, String dictDataName, String dictDataValue, String remark, String dictDataSort, String createTime) {
        this.dictDataId = dictDataId;
        this.dictId = dictId;
        this.dictKey = dictKey;
        this.dictDataName = dictDataName;
        this.dictDataValue = dictDataValue;
        this.remark = remark;
        this.dictDataSort = dictDataSort;
        this.createTime = createTime;
    }

    public Long getDictDataId() {
        return dictDataId;
    }

    public void setDictDataId(Long dictDataId) {
        this.dictDataId = dictDataId;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
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

    public String getDictDataName() {
        return dictDataName;
    }

    public void setDictDataName(String dictDataName) {
        this.dictDataName = dictDataName;
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
}
