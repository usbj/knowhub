package com.rookie.system.pojo.vo;

/**
 * 通知按类型分组计数承载结构。
 * <p>
 * 由 {@code SysNoticeMapper.countMyNoticesByType} 的 GROUP BY notice_type 查询填充，
 * 每行对应一个通知类型（sys_notice_type 字典 code：NOTICE/NOTIFY/REMIND）与该类型当前用户可见通知数。
 * service 层将其转成 {@code Map<String, Long>} 并补一行 ALL（各类型求和）返回给前端，
 * 供前台 /notices 分类 tab 与 profile 消息子 tab 显示真实总数角标（而非当前页数据量）。
 */
public class NoticeTypeCount {

    /** 通知类型字典 code（NOTICE/NOTIFY/REMIND） */
    private String noticeType;

    /** 该类型当前用户可见通知数 */
    private Long count;

    public NoticeTypeCount() {
    }

    public String getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(String noticeType) {
        this.noticeType = noticeType;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
