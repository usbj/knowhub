package com.rookie.system.pojo.quarry;



public class DictDataQuarry {

    Long dictId;

    String dictKey;

    String dictDataLabel;

    public DictDataQuarry(Long dictId, String dictKey, String dictDataLabel) {
        this.dictId = dictId;
        this.dictKey = dictKey;
        this.dictDataLabel = dictDataLabel;
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

    public String getDictDataLabel() {
        return dictDataLabel;
    }

    public void setDictDataLabel(String dictDataLabel) {
        this.dictDataLabel = dictDataLabel;
    }

    @Override
    public String toString() {
        return "DictDataQuarry{" +
                "dictId=" + dictId +
                ", dictKey='" + dictKey + '\'' +
                ", dictDataLabel='" + dictDataLabel + '\'' +
                '}';
    }
}
