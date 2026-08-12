package com.knowhub.mapper.common;

import com.rookie.common.pojo.entity.SysNotice;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 前台公开公告只读 Mapper（knowhub 侧自包含，不依赖 rookie-system 的 SysNoticeMapper）。
 * <p>
 * 铁律：仅查 publish_scope='ALL'（群发）AND status='PUBLISHED' AND delete=0 的公告——
 * 分组/指定成员私发公告对未登录访客不可见，公开列表/详情均不下发。
 */
public interface NoticePortalMapper {

    /**
     * 公开公告列表（群发 + 已发布 + 未删），可选按 noticeType 过滤，置顶优先+发布时间倒序。
     */
    List<SysNotice> listPublicNotices(@Param("noticeType") String noticeType);

    /**
     * 公开公告单条（同上 where 条件 + notice_id），命中返实体，非 ALL 范围/未发布/不存在返 null。
     */
    SysNotice getPublicNoticeById(@Param("noticeId") Long noticeId);
}