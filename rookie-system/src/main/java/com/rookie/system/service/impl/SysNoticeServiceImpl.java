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
import com.rookie.system.pojo.SysNoticeGroupRel;
import com.rookie.system.pojo.SysNoticeRead;
import com.rookie.system.pojo.quarry.NoticeQuarry;
import com.rookie.system.pojo.vo.SysNoticeVo;
import com.rookie.system.service.SysNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
        return true;
    }

    @Override
    @Transactional
    public Boolean editSysNoticeInfo(SysNoticeVo vo) {
        sysNoticeGroupRelMapper.deleteSysNoticeGroupRelByNoticeId(vo.getNoticeId());
        addGroupRelIfNeeded(vo);

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

    @Override
    public List<SysNoticeVo> getMyNotices(Long userId) {
        List<SysNotice> list = sysNoticeMapper.getNoticesForUser(userId);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
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

        return list.stream().map(n -> {
            SysNoticeVo vo = BeanUtil.toBean(n, SysNoticeVo.class);
            vo.setHasRead(readNoticeIds.contains(n.getNoticeId()));
            vo.setHasConfirmed(confirmedNoticeIds.contains(n.getNoticeId()));
            return vo;
        }).collect(Collectors.toList());
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
}
