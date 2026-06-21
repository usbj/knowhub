package com.rookie.system.pojo;

import java.util.Date;

public class SysNoticeRead {

    private Long id;

    private Long noticeId;

    private Long userId;

    private Date readTime;

    private Integer confirmStatus;

    private Date confirmTime;

    public SysNoticeRead() {
    }

    public SysNoticeRead(Long id, Long noticeId, Long userId, Date readTime, Integer confirmStatus, Date confirmTime) {
        this.id = id;
        this.noticeId = noticeId;
        this.userId = userId;
        this.readTime = readTime;
        this.confirmStatus = confirmStatus;
        this.confirmTime = confirmTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Date getReadTime() {
        return readTime;
    }

    public void setReadTime(Date readTime) {
        this.readTime = readTime;
    }

    public Integer getConfirmStatus() {
        return confirmStatus;
    }

    public void setConfirmStatus(Integer confirmStatus) {
        this.confirmStatus = confirmStatus;
    }

    public Date getConfirmTime() {
        return confirmTime;
    }

    public void setConfirmTime(Date confirmTime) {
        this.confirmTime = confirmTime;
    }
}
