package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

public class SysNotice extends BaseEntity {

    private Long noticeId;

    private String title;

    private String content;

    private String noticeType;

    private String level;

    private String publishScope;

    private String status;

    private Integer isTop;

    private Integer needConfirm;

    private Date publishTime;

    private Date expireTime;

    private String routePath;

    private String remark;

    private Integer delete;

    public SysNotice() {
    }

    public SysNotice(Date createTime, Date updateTime, String createBy, String updateBy, Long noticeId, String title, String content, String noticeType, String level, String publishScope, String status, Integer isTop, Integer needConfirm, Date publishTime, Date expireTime, String routePath, String remark, Integer delete) {
        super(createTime, updateTime, createBy, updateBy);
        this.noticeId = noticeId;
        this.title = title;
        this.content = content;
        this.noticeType = noticeType;
        this.level = level;
        this.publishScope = publishScope;
        this.status = status;
        this.isTop = isTop;
        this.needConfirm = needConfirm;
        this.publishTime = publishTime;
        this.expireTime = expireTime;
        this.routePath = routePath;
        this.remark = remark;
        this.delete = delete;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(Long noticeId) {
        this.noticeId = noticeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public Integer getIsTop() {
        return isTop;
    }

    public void setIsTop(Integer isTop) {
        this.isTop = isTop;
    }

    public Integer getNeedConfirm() {
        return needConfirm;
    }

    public void setNeedConfirm(Integer needConfirm) {
        this.needConfirm = needConfirm;
    }

    public Date getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Date publishTime) {
        this.publishTime = publishTime;
    }

    public Date getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Date expireTime) {
        this.expireTime = expireTime;
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getDelete() {
        return delete;
    }

    public void setDelete(Integer delete) {
        this.delete = delete;
    }

    @Override
    public String toString() {
        return "SysNotice{" +
                "noticeId=" + noticeId +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", noticeType='" + noticeType + '\'' +
                ", level='" + level + '\'' +
                ", publishScope='" + publishScope + '\'' +
                ", status='" + status + '\'' +
                ", isTop=" + isTop +
                ", needConfirm=" + needConfirm +
                ", publishTime=" + publishTime +
                ", expireTime=" + expireTime +
                ", routePath='" + routePath + '\'' +
                ", remark='" + remark + '\'' +
                ", delete=" + delete +
                ", createTime=" + getCreateTime() +
                ", updateTime=" + getUpdateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                '}';
    }
}
