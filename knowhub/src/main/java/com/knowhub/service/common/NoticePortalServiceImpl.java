package com.knowhub.service.common;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.mapper.common.NoticePortalMapper;
import com.knowhub.mapper.common.NoticeReadMapper;
import com.knowhub.pojo.common.vo.NoticePortalVo;
import com.knowhub.service.common.impl.NoticePortalService;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysNotice;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 前台公开公告 Service 实现。
 * <p>
 * 复用 com.rookie.common.pojo.entity.SysNotice 只读实体（rookie-common 是 knowhub 已依赖的公共层，
 * 未登录访客也可访问的公开公告，无需登录态、无读写态回填）。仅群发（publish_scope=ALL）+ 已发布
 * 公告下发，分组/指定成员私发公告对访客不可见（由 mapper SQL 铁律保证）。
 * <p>
 * 登录用户额外回填 hasConfirmed（sys_notice_read.confirm_status=1）——确认落库与后台
 * /sys/notice/confirm 同表同语义，前端"确认"按钮仅对登录用户且 needConfirm=1 的公告露出。
 */
@Service
public class NoticePortalServiceImpl implements NoticePortalService {

    @Autowired
    NoticePortalMapper noticePortalMapper;

    @Autowired
    NoticeReadMapper noticeReadMapper;

    @Override
    public PageInfo<NoticePortalVo> listPublicNotices(String noticeType) {
        PageUtil.startPage();
        List<SysNotice> list = noticePortalMapper.listPublicNotices(noticeType);
        PageInfo<SysNotice> entityPage = new PageInfo<>(list);
        // entity→vo 走 PageUtil.copyPageInfo，避免分页 total/页码丢失
        PageInfo<NoticePortalVo> voPage = PageUtil.copyPageInfo(entityPage, NoticePortalVo.class);
        // 需登录态回填确认态：未登录 hasConfirmed 恒 false，前端不依赖该值露按钮
        fillHasConfirmed(voPage.getList(), currentUserOrNull());
        return voPage;
    }

    @Override
    public NoticePortalVo getPublicNoticeById(Long noticeId) {
        SysNotice notice = noticePortalMapper.getPublicNoticeById(noticeId);
        if (notice == null) {
            return null;
        }
        NoticePortalVo vo = BeanUtil.toBean(notice, NoticePortalVo.class);
        UserInfo user = currentUserOrNull();
        vo.setHasConfirmed(Boolean.FALSE);
        if (user != null) {
            Integer status = noticeReadMapper.getConfirmStatus(noticeId, user.getUserId());
            vo.setHasConfirmed(status != null && status == 1);
        }
        return vo;
    }

    @Override
    public NoticePortalVo confirmNotice(Long noticeId, Long userId) {
        // 仅登录用户可达：permitAll 区 controller 取不到 UserInfo 时直接抛登录要求
        if (userId == null) {
            throw new ServiceException(401, "请登录后再确认");
        }
        SysNotice notice = noticePortalMapper.getPublicNoticeById(noticeId);
        if (notice == null) {
            throw new ServiceException(404, "公告不存在或不可见");
        }
        if (notice.getNeedConfirm() == null || notice.getNeedConfirm() != 1) {
            throw new ServiceException(400, "该公告无需确认");
        }
        // 与后台 SysNoticeServiceImpl.confirmNotice 同语义：无读记录插一行带 confirm_status=1，有则置 1
        Integer existing = noticeReadMapper.getConfirmStatus(noticeId, userId);
        if (existing == null) {
            noticeReadMapper.insertConfirmRecord(noticeId, userId);
        } else if (existing != 1) {
            noticeReadMapper.updateConfirmStatus(noticeId, userId);
        }
        return getPublicNoticeById(noticeId);
    }

    /**
     * 列表批量回填 hasConfirmed：登录用户取本页 noticeId 集合一次性查确认记录，未登录全 false。
     * 设置为 false（而非 null）便于前端直接判空逻辑，未登录访客不露确认按钮。
     */
    private void fillHasConfirmed(List<NoticePortalVo> list, UserInfo user) {
        if (list == null || list.isEmpty()) return;
        for (NoticePortalVo vo : list) {
            vo.setHasConfirmed(Boolean.FALSE);
        }
        if (user == null) return;
        List<Long> ids = list.stream()
                .map(NoticePortalVo::getNoticeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (ids.isEmpty()) return;
        List<Long> confirmedIds = noticeReadMapper.getConfirmedNoticeIds(user.getUserId(), ids);
        Set<Long> confirmedSet = confirmedIds == null ? Collections.emptySet() : new HashSet<>(confirmedIds);
        for (NoticePortalVo vo : list) {
            if (vo.getNoticeId() != null && confirmedSet.contains(vo.getNoticeId())) {
                vo.setHasConfirmed(true);
            }
        }
    }

    /**
     * 前台 permitAll 区安全取登录态：auth 为空/未认证/principal 非 UserInfo 均返 null，
     * 与 BlogPortalServiceImpl.currentUserOrNull 同范式（前台 permitAll 区不能像后台那样直接强转）。
     */
    private UserInfo currentUserOrNull() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        return (p instanceof UserInfo) ? (UserInfo) p : null;
    }
}