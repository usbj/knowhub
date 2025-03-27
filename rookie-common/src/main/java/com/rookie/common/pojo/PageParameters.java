package com.rookie.common.pojo;

public class PageParameters {


    //页数
    private int pageNum=1;

    //每页大小
    private int pageSize=10;

    //排序的列
    private String sortTheSequence;

    private boolean reasonable=true;

    //排序方式
    private String isAsc = "asc";

    public PageParameters() {
    }

    public PageParameters(int pageNum, int pageSize, String sortTheSequence, boolean reasonable, String isAsc) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.sortTheSequence = sortTheSequence;
        this.reasonable = reasonable;
        this.isAsc = isAsc;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getSortTheSequence() {
        return sortTheSequence;
    }

    public void setSortTheSequence(String sortTheSequence) {
        this.sortTheSequence = sortTheSequence;
    }

    public String getIsAsc() {
        return isAsc;
    }

    public void setIsAsc(String isAsc) {
        this.isAsc = isAsc;
    }

    public boolean isReasonable() {
        return reasonable;
    }

    public void setReasonable(boolean reasonable) {
        this.reasonable = reasonable;
    }

    @Override
    public String toString() {
        return "PageParameters{" +
                "pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                ", sortTheSequence='" + sortTheSequence + '\'' +
                ", reasonable=" + reasonable +
                ", isAsc='" + isAsc + '\'' +
                '}';
    }
}
