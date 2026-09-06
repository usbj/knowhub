package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.system.pojo.quarry.NoticeQuarry;
import com.rookie.system.pojo.vo.SysNoticeVo;

import java.util.Map;

public interface SysNoticeService {

    PageInfo<SysNoticeVo> quarrySysNotice(NoticeQuarry quarry);

    SysNoticeVo getSysNoticeInfo(Long noticeId);

    Boolean addSysNoticeInfo(SysNoticeVo vo);

    Boolean editSysNoticeInfo(SysNoticeVo vo);

    Boolean deleteSysNoticeInfo(Long[] noticeIds);

    Boolean publishSysNotice(Long noticeId);

    Boolean revokeSysNotice(Long noticeId);

    /**
     * 分页获取当前用户可见的通知（按 is_top / publish_time / notice_id 排序），
     * 每页逐条装配 hasRead / hasConfirmed 后返回 PageInfo。
     * 分页参数 pageNum / pageSize 由 PageUtil 从请求参数读取（默认 1 / 10）。
     *
     * @param userId     当前用户 id
     * @param noticeType 可选通知类型过滤（NOTICE/NOTIFY/REMIND），null/空 表示全部。供前台 /notices 页类型 tab 复用。
     */
    PageInfo<SysNoticeVo> getMyNotices(Long userId, String noticeType);

    Long countUnreadNotices(Long userId);

    Boolean markAsRead(Long noticeId, Long userId);

    /**
     * 全部已读：批量标记当前用户所有可见且未读的通知为已读。
     * 一次性 INSERT...SELECT 覆盖所有未读（含懒加载下拉未加载页），前端徽标随之清零。
     *
     * @param userId 当前用户 id
     * @return 是否成功
     */
    Boolean markAllAsRead(Long userId);

    Boolean confirmNotice(Long noticeId, Long userId);

    /**
     * 按通知类型聚合统计当前用户可见通知数，返回 {@code Map<noticeType, count>}。
     * 含一个 ALL 键（各类型求和），以及各实际存在的类型键（NOTICE/NOTIFY/REMIND）。
     * 供前台分类 tab / 消息子 tab 显示真实总数角标（不随分页当前页数据量变）。
     *
     * @param userId 当前用户 id
     * @return 各类型计数 Map，至少含 ALL 键（无可见通知时 ALL=0）
     */
    Map<String, Long> countMyNoticesByType(Long userId);
}
