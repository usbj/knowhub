package com.knowhub.mapper.common;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公告已读/确认事实表只读 + 确认 upsert Mapper（knowhub 侧自包含，不依赖 rookie-system 的 SysNoticeReadMapper）。
 * <p>
 * 对应 sys_notice_read 表（notice_id + user_id 唯一）。前台公开页只在登录用户点"确认"时 upsert 一行：
 * 已有读记录则把 confirm_status 置 1，无则插入一行带 read_time + confirm_status=1。
 * 与后台 SysNoticeServiceImpl.confirmNotice 写同一张表、同一语义。
 */
public interface NoticeReadMapper {

    /**
     * 查当前用户对某公告的确认态。
     * 返回 confirm_status（1=已确认，0=已读未确认）；无记录返 null。
     */
    Integer getConfirmStatus(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /**
     * 批量查当前用户在指定公告集合中已确认（confirm_status=1）的 noticeId 集合。
     * 用于公开列表回填 hasConfirmed（避免逐条 N+1）。noticeIds 为空返空集合。
     */
    List<Long> getConfirmedNoticeIds(@Param("userId") Long userId, @Param("noticeIds") List<Long> noticeIds);

    /**
     * 插入一条读记录（read_time + confirm_status=1），用于"确认"时该用户尚无读记录的场景。
     * 由 NoticePortalService.confirmNotice 在 getConfirmStatus 返 null 时调用。
     */
    int insertConfirmRecord(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /**
     * 把已有读记录的 confirm_status 置 1。
     * 由 NoticePortalService.confirmNotice 在 getConfirmStatus 返 0（已读未确认）时调用。
     */
    int updateConfirmStatus(@Param("noticeId") Long noticeId, @Param("userId") Long userId);
}