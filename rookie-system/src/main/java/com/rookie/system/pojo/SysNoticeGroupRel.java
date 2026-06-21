package com.rookie.system.pojo;

public class SysNoticeGroupRel {

    private Long id;

    private Long noticeId;

    private Long groupId;

    public SysNoticeGroupRel() {
    }

    public SysNoticeGroupRel(Long id, Long noticeId, Long groupId) {
        this.id = id;
        this.noticeId = noticeId;
        this.groupId = groupId;
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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
