package com.rookie.system.pojo.quarry;



public class DictDataQuarry {

    Long dictId;

    String dictKey;

    String dictDataName;

    public DictDataQuarry(Long dictId, String dictKey, String dictDataName) {
        this.dictId = dictId;
        this.dictKey = dictKey;
        this.dictDataName = dictDataName;
    }

    public DictDataQuarry() {
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

    @Override
    public String toString() {
        return "DictDataQuarry{" +
                "dictId=" + dictId +
                ", dictKey='" + dictKey + '\'' +
                ", dictDataName='" + dictDataName + '\'' +
                '}';
    }
}
