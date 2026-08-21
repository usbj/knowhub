package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysNotice;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysNoticeGroupRelMapper;
import com.rookie.system.mapper.SysNoticeMapper;
import com.rookie.system.mapper.SysNoticeReadMapper;
import com.rookie.system.mapper.SysNoticeUserRelMapper;
import com.rookie.system.pojo.SysNoticeGroupRel;
import com.rookie.system.pojo.SysNoticeRead;
import com.rookie.system.pojo.SysNoticeUserRel;
import com.rookie.system.pojo.quarry.NoticeQuarry;
import com.rookie.system.pojo.vo.NoticeTargetUserVo;
import com.rookie.system.pojo.vo.NoticeTypeCount;
import com.rookie.system.pojo.vo.SysNoticeVo;
import com.rookie.system.service.SysNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysNoticeServiceImpl implements SysNoticeService {

    @Autowired
    SysNoticeMapper sysNoticeMapper;

    @Autowired
    SysNoticeGroupRelMapper sysNoticeGroupRelMapper;

    @Autowired
    SysNoticeReadMapper sysNoticeReadMapper;

    @Autowired
    SysNoticeUserRelMapper sysNoticeUserRelMapper;

    @Override
    public PageInfo<SysNoticeVo> quarrySysNotice(NoticeQuarry quarry) {
        PageUtil.startPage();
        List<SysNotice> list = sysNoticeMapper.quarrySysNotice(quarry);
        PageInfo<SysNotice> page = PageUtil.packagedPageInfo(list);
        return PageUtil.copyPageInfo(page, SysNoticeVo.class);
    }

    @Override
    public SysNoticeVo getSysNoticeInfo(Long noticeId) {
        SysNotice notice = sysNoticeMapper.getSysNoticeInfoById(noticeId);
        SysNoticeVo vo = BeanUtil.toBean(notice, SysNoticeVo.class);
        List<SysNoticeGroupRel> rels = sysNoticeGroupRelMapper.getSysNoticeGroupRelByNoticeId(noticeId);
        if (rels != null && !rels.isEmpty()) {
            vo.setGroupIds(rels.stream().map(SysNoticeGroupRel::getGroupId).collect(Collectors.toList()));
        }
        // 指定成员回显：targetUserIds 用于编辑提交，targetUsers 用于弹窗展示已选成员信息
        List<SysNoticeUserRel> userRels = sysNoticeUserRelMapper.getSysNoticeUserRelByNoticeId(noticeId);
        if (userRels != null && !userRels.isEmpty()) {
            vo.setTargetUserIds(userRels.stream().map(SysNoticeUserRel::getUserId).collect(Collectors.toList()));
        }
        List<NoticeTargetUserVo> targetUsers = sysNoticeUserRelMapper.getTargetUsersByNoticeId(noticeId);
        if (targetUsers != null && !targetUsers.isEmpty()) {
            vo.setTargetUsers(targetUsers);
        }
        return vo;
    }

    @Override
    @Transactional
    public Boolean addSysNoticeInfo(SysNoticeVo vo) {
        SysNotice notice = BeanUtil.toBean(vo, SysNotice.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        notice.setCreateBy(userInfo.getUsername());
        notice.setUpdateBy(userInfo.getUsername());
        notice.setCreateTime(new Date());
        notice.setUpdateTime(new Date());
        try {
            sysNoticeMapper.addSysNotice(notice);
        } catch (Exception e) {
            throw new ServiceException(500, "消息通知添加失败", e.getMessage());
        }
        vo.setNoticeId(notice.getNoticeId());
        addGroupRelIfNeeded(vo);
        addTargetUserRelIfNeeded(vo);
        return true;
    }

    @Override
    @Transactional
    public Boolean editSysNoticeInfo(SysNoticeVo vo) {
        sysNoticeGroupRelMapper.deleteSysNoticeGroupRelByNoticeId(vo.getNoticeId());
        addGroupRelIfNeeded(vo);
        sysNoticeUserRelMapper.deleteSysNoticeUserRelByNoticeId(vo.getNoticeId());
        addTargetUserRelIfNeeded(vo);

        SysNotice notice = BeanUtil.toBean(vo, SysNotice.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        notice.setUpdateBy(userInfo.getUsername());
        try {
            sysNoticeMapper.editSysNoticeInfo(notice);
        } catch (Exception e) {
            throw new ServiceException(500, "消息通知修改失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteSysNoticeInfo(Long[] noticeIds) {
        try {
            for (Long id : noticeIds) {
                sysNoticeGroupRelMapper.deleteSysNoticeGroupRelByNoticeId(id);
                sysNoticeUserRelMapper.deleteSysNoticeUserRelByNoticeId(id);
                sysNoticeReadMapper.deleteSysNoticeReadByNoticeId(id);
                sysNoticeMapper.softDeleteSysNotice(id);
            }
        } catch (Exception e) {
            throw new ServiceException(500, "消息通知删除失败", e.getMessage());
        }
        return true;
    }

    @Override
    public Boolean publishSysNotice(Long noticeId) {
        SysNotice notice = sysNoticeMapper.getSysNoticeInfoById(noticeId);
        if (notice == null) {
            throw new ServiceException(500, "消息通知不存在");
        }
        notice.setStatus("PUBLISHED");
        notice.setPublishTime(new Date());
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        notice.setUpdateBy(userInfo.getUsername());
        sysNoticeMapper.editSysNoticeInfo(notice);
        return true;
    }

    @Override
    public Boolean revokeSysNotice(Long noticeId) {
        SysNotice notice = sysNoticeMapper.getSysNoticeInfoById(noticeId);
        if (notice == null) {
            throw new ServiceException(500, "消息通知不存在");
        }
        notice.setStatus("REVOKED");
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        notice.setUpdateBy(userInfo.getUsername());
        sysNoticeMapper.editSysNoticeInfo(notice);
        return true;
    }

    /**
     * 分页获取当前用户可见的通知（按 is_top / publish_time / notice_id 排序），
     * 每页逐条装配 hasRead / hasConfirmed 后返回 PageInfo。
     * 分页参数 pageNum / pageSize 由 PageUtil 从请求参数读取（默认 1 / 10）。
     */
    @Override
    public PageInfo<SysNoticeVo> getMyNotices(Long userId, String noticeType) {
        PageUtil.startPage();
        List<SysNotice> list = sysNoticeMapper.getNoticesForUser(userId, noticeType);
        if (list == null || list.isEmpty()) {
            return new PageInfo<>(new ArrayList<>());
        }

        List<SysNoticeRead> reads = sysNoticeReadMapper.getSysNoticeReadByUserId(userId);
        Set<Long> readNoticeIds = reads.stream()
                .filter(r -> r.getReadTime() != null)
                .map(SysNoticeRead::getNoticeId)
                .collect(Collectors.toSet());
        Set<Long> confirmedNoticeIds = reads.stream()
                .filter(r -> r.getConfirmStatus() != null && r.getConfirmStatus() == 1)
                .map(SysNoticeRead::getNoticeId)
                .collect(Collectors.toSet());

        PageInfo<SysNotice> page = PageUtil.packagedPageInfo(list);
        PageInfo<SysNoticeVo> pageInfo = PageUtil.copyPageInfo(page, SysNoticeVo.class);
        for (SysNoticeVo vo : pageInfo.getList()) {
            vo.setHasRead(readNoticeIds.contains(vo.getNoticeId()));
            vo.setHasConfirmed(confirmedNoticeIds.contains(vo.getNoticeId()));
        }
        return pageInfo;
    }

    @Override
    public Long countUnreadNotices(Long userId) {
        Long count = sysNoticeMapper.countUnreadNoticesForUser(userId);
        return count == null ? 0L : count;
    }

    @Override
    public Boolean markAsRead(Long noticeId, Long userId) {
        SysNoticeRead existing = sysNoticeReadMapper.getByNoticeAndUser(noticeId, userId);
        if (existing != null) {
            return true;
        }
        SysNoticeRead record = new SysNoticeRead();
        record.setNoticeId(noticeId);
        record.setUserId(userId);
        record.setReadTime(new Date());
        sysNoticeReadMapper.addSysNoticeRead(record);
        return true;
    }

    @Override
    public Boolean markAllAsRead(Long userId) {
        // INSERT...SELECT 批量插入所有可见且未读通知的已读记录，覆盖懒加载下拉未加载页的未读。
        // 已读通知 not exists 过滤不会重复插入；无未读时插入 0 行仍返回 true。
        sysNoticeMapper.markAllReadForUser(userId);
        return true;
    }

    @Override
    public Boolean confirmNotice(Long noticeId, Long userId) {
        SysNoticeRead existing = sysNoticeReadMapper.getByNoticeAndUser(noticeId, userId);
        if (existing == null) {
            SysNoticeRead record = new SysNoticeRead();
            record.setNoticeId(noticeId);
            record.setUserId(userId);
            record.setReadTime(new Date());
            record.setConfirmStatus(1);
            record.setConfirmTime(new Date());
            sysNoticeReadMapper.addSysNoticeRead(record);
        } else if (existing.getConfirmStatus() == null || existing.getConfirmStatus() == 0) {
            existing.setConfirmStatus(1);
            existing.setConfirmTime(new Date());
            sysNoticeReadMapper.editSysNoticeReadInfo(existing);
        }
        return true;
    }

    /**
     * 按通知类型聚合统计当前用户可见通知数。
     * mapper GROUP BY notice_type 返回各类型计数行，这里转成 {@code Map<noticeType, count>}，
     * 并补一行 ALL（各类型求和）——前端分类 tab / 消息子 tab 角标直接按 key 取真实总数，
     * 不必为四个角标发四次分页请求读 total。无可见通知时返 {ALL:0}。
     */
    @Override
    public Map<String, Long> countMyNoticesByType(Long userId) {
        List<NoticeTypeCount> rows = sysNoticeMapper.countMyNoticesByType(userId);
        Map<String, Long> result = new HashMap<>();
        long all = 0L;
        if (rows != null) {
            for (NoticeTypeCount row : rows) {
                long c = row.getCount() == null ? 0L : row.getCount();
                result.put(row.getNoticeType(), c);
                all += c;
            }
        }
        result.put("ALL", all);
        return result;
    }

    private void addGroupRelIfNeeded(SysNoticeVo vo) {
        if (vo.getGroupIds() == null || vo.getGroupIds().isEmpty()) {
            return;
        }
        List<SysNoticeGroupRel> rels = new ArrayList<>();
        for (Long groupId : vo.getGroupIds()) {
            rels.add(new SysNoticeGroupRel(null, vo.getNoticeId(), groupId));
        }
        try {
            sysNoticeGroupRelMapper.insertSysNoticeGroupRel(rels);
        } catch (Exception e) {
            throw new ServiceException(500, "消息分组关联插入失败", e.getMessage());
        }
    }

    /**
     * 发布范围为"指定成员"(publish_scope=USER) 时，按 vo.targetUserIds 批量写入 sys_notice_user_rel。
     * 仅在携带了非空 targetUserIds 时生效；全员/分组范围下应由调用方清空该字段，避免脏数据。
     */
    private void addTargetUserRelIfNeeded(SysNoticeVo vo) {
        if (vo.getTargetUserIds() == null || vo.getTargetUserIds().isEmpty()) {
            return;
        }
        List<SysNoticeUserRel> userRels = new ArrayList<>();
        for (Long userId : vo.getTargetUserIds()) {
            userRels.add(new SysNoticeUserRel(null, vo.getNoticeId(), userId));
        }
        try {
            sysNoticeUserRelMapper.insertSysNoticeUserRel(userRels);
        } catch (Exception e) {
            throw new ServiceException(500, "消息指定成员关联插入失败", e.getMessage());
        }
    }
}
