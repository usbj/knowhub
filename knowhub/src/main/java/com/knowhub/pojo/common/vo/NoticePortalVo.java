package com.knowhub.pojo.common.vo;

import java.util.Date;

/**
 * 前台公开公告 VO（GET /portal/notice/* 出参）。
 * <p>
 * 与 rookie-system 的 SysNoticeVo 区别：前台公开公告只暴露群发（publish_scope=ALL）的已发布公告，
 * 不下发分组/指定成员的目标用户集合、不回填当前用户已读/已确认态（公开列表对未登录访客也开放，
 * 无登录态可回填）。仅含展示所需字段。
 */
public class NoticePortalVo {

    private Long noticeId;

    private String title;

    private String content;

    private String noticeType;

    private String level;

    private Integer isTop;

    /** 是否需要确认（sys_notice.need_confirm，0/1），公开公告只下发该开关，前端据此决定是否露"确认"按钮 */
    private Integer needConfirm;

    /**
     * 当前登录用户是否已确认（sys_notice_read.confirm_status=1）。
     * 仅登录用户回填：未登录访客恒为 false（无登录态可回填）；
     * needConfirm=0 的公告也恒为 false（前端不依赖该值，仅按 needConfirm+登录态露按钮）。
     */
    private Boolean hasConfirmed;

    /** 发布时间（sys_notice.publish_time，Date 经 jackson 全局格式化出字符串） */
    private Date publishTime;

    /** 失效时间，可为 null */
    private Date expireTime;

    /** 发布者（sys_notice.create_by） */
    private String createBy;

    private Date createTime;

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

    public Integer getIsTop() {
        return isTop;
    }

    public void setIsTop(Integer isTop) {
        this.isTop = isTop;
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Integer getNeedConfirm() {
        return needConfirm;
    }

    public void setNeedConfirm(Integer needConfirm) {
        this.needConfirm = needConfirm;
    }

    public Boolean getHasConfirmed() {
        return hasConfirmed;
    }

    public void setHasConfirmed(Boolean hasConfirmed) {
        this.hasConfirmed = hasConfirmed;
    }
}