package com.knowhub.service.common.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.common.vo.NoticePortalVo;

/**
 * 前台公开公告 Service。
 * <p>
 * 仅下发群发（publish_scope=ALL）+ 已发布公告，对未登录访客开放；
 * 不回填已读/已确认态（无登录态），不下发分组/指定成员私发公告。
 */
public interface NoticePortalService {

    /**
     * 公开公告分页列表。分页参数由请求参数 pageNum/pageSize 提供（PageUtil.startPage），
     * 可选 noticeType 过滤（对应 /notices 页类型筛选 tab）。
     */
    PageInfo<NoticePortalVo> listPublicNotices(String noticeType);

    /**
     * 公开公告详情。命中返完整正文，否则返 null（controller 转 404，防私发公告被穿透）。
     * 登录用户回填 hasConfirmed（sys_notice_read.confirm_status），未登录恒为 false。
     */
    NoticePortalVo getPublicNoticeById(Long noticeId);

    /**
     * 确认公告（仅登录用户，needConfirm=1 的群发已发布公告）。
     * 未登录调用由 controller 侧拦截（permitAll 区拿不到 UserInfo），service 内再校验公告确实
     * 存在且 needConfirm=1，再 upsert sys_notice_read（与后台 /sys/notice/confirm 同表同语义）。
     * 返回最新 hasConfirmed=true 的 VO，供前端乐观更新兜底校验。
     */
    NoticePortalVo confirmNotice(Long noticeId, Long userId);
}