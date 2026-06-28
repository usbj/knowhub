package com.rookie.system.pojo.quarry;

import java.util.Date;

public class NoticeQuarry {

    private String title;

    private String noticeType;

    private String level;

    private String publishScope;

    private String status;

    private Date beginTime;

    private Date endTime;

    public NoticeQuarry() {
    }

    public NoticeQuarry(String title, String noticeType, String level, String publishScope, String status, Date beginTime, Date endTime) {
        this.title = title;
        this.noticeType = noticeType;
        this.level = level;
        this.publishScope = publishScope;
        this.status = status;
        this.beginTime = beginTime;
        this.endTime = endTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(String noticeType) {
        this.noticeType = noticeType;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getPublishScope() {
        return publishScope;
    }

    public void setPublishScope(String publishScope) {
        this.publishScope = publishScope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Date beginTime) {
        this.beginTime = beginTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "NoticeQuarry{" +
                "title='" + title + '\'' +
                ", noticeType='" + noticeType + '\'' +
                ", level='" + level + '\'' +
                ", publishScope='" + publishScope + '\'' +
                ", status='" + status + '\'' +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}
