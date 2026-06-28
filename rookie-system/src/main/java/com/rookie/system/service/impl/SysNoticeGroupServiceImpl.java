package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysNoticeGroup;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysNoticeGroupMapper;
import com.rookie.system.mapper.SysNoticeGroupMemberMapper;
import com.rookie.system.mapper.SysNoticeGroupRelMapper;
import com.rookie.system.pojo.SysNoticeGroupMember;
import com.rookie.system.pojo.quarry.NoticeGroupQuarry;
import com.rookie.system.pojo.vo.SysNoticeGroupVo;
import com.rookie.system.service.SysNoticeGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysNoticeGroupServiceImpl implements SysNoticeGroupService {

    @Autowired
    SysNoticeGroupMapper sysNoticeGroupMapper;

    @Autowired
    SysNoticeGroupMemberMapper sysNoticeGroupMemberMapper;

    @Autowired
    SysNoticeGroupRelMapper sysNoticeGroupRelMapper;

    @Override
    public PageInfo<SysNoticeGroupVo> quarrySysNoticeGroup(NoticeGroupQuarry quarry) {
        PageUtil.startPage();
        List<SysNoticeGroup> groups = sysNoticeGroupMapper.quarrySysNoticeGroup(quarry);
        PageInfo<SysNoticeGroup> page = PageUtil.packagedPageInfo(groups);
        PageInfo<SysNoticeGroupVo> voPage = PageUtil.copyPageInfo(page, SysNoticeGroupVo.class);
        for (int i = 0; i < groups.size(); i++) {
            SysNoticeGroupVo vo = voPage.getList().get(i);
            List<SysNoticeGroupMember> members = sysNoticeGroupMemberMapper.getSysNoticeGroupMemberByGroupId(groups.get(i).getGroupId());
            vo.setMembers(members);
        }
        return voPage;
    }

    @Override
    public SysNoticeGroupVo getSysNoticeGroupInfo(Long groupId) {
        SysNoticeGroup group = sysNoticeGroupMapper.getSysNoticeGroupInfoById(groupId);
        SysNoticeGroupVo vo = BeanUtil.toBean(group, SysNoticeGroupVo.class);
        List<SysNoticeGroupMember> members = sysNoticeGroupMemberMapper.getSysNoticeGroupMemberByGroupId(groupId);
        vo.setMembers(members);
        return vo;
    }

    @Override
    @Transactional
    public Boolean addSysNoticeGroupInfo(SysNoticeGroupVo vo) {
        SysNoticeGroup group = BeanUtil.toBean(vo, SysNoticeGroup.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        group.setCreateBy(userInfo.getUsername());
        group.setUpdateBy(userInfo.getUsername());
        group.setCreateTime(new Date());
        group.setUpdateTime(new Date());
        try {
            sysNoticeGroupMapper.addSysNoticeGroup(group);
        } catch (Exception e) {
            throw new ServiceException(500, "通知分组添加失败", e.getMessage());
        }
        vo.setGroupId(group.getGroupId());
        addMembersInternal(vo.getGroupId(), vo.getMembers());
        return true;
    }

    @Override
    public Boolean editSysNoticeGroupInfo(SysNoticeGroupVo vo) {
        SysNoticeGroup group = BeanUtil.toBean(vo, SysNoticeGroup.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        group.setUpdateBy(userInfo.getUsername());
        try {
            sysNoticeGroupMapper.editSysNoticeGroupInfo(group);
        } catch (Exception e) {
            throw new ServiceException(500, "通知分组修改失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteSysNoticeGroupInfo(Long[] groupIds) {
        try {
            for (Long id : groupIds) {
                sysNoticeGroupRelMapper.deleteSysNoticeGroupRelByGroupId(id);
                sysNoticeGroupMemberMapper.deleteSysNoticeGroupMemberByGroupId(id);
                sysNoticeGroupMapper.deleteSysNoticeGroupById(id);
            }
        } catch (Exception e) {
            throw new ServiceException(500, "通知分组删除失败", e.getMessage());
        }
        return true;
    }

    @Override
    public Boolean addMembers(Long groupId, List<Long> userIds) {
        List<SysNoticeGroupMember> members = userIds.stream()
                .map(uid -> new SysNoticeGroupMember(null, groupId, uid))
                .collect(Collectors.toList());
        try {
            sysNoticeGroupMemberMapper.insertSysNoticeGroupMember(members);
        } catch (Exception e) {
            throw new ServiceException(500, "分组成员添加失败", e.getMessage());
        }
        return true;
    }

    @Override
    public Boolean removeMembers(Long groupId, List<Long> memberIds) {
        try {
            List<SysNoticeGroupMember> existing = sysNoticeGroupMemberMapper.getSysNoticeGroupMemberByGroupId(groupId);
            for (Long mid : memberIds) {
                boolean belongs = existing.stream().anyMatch(m -> m.getId().equals(mid));
                if (!belongs) {
                    throw new ServiceException(500, "成员 " + mid + " 不属于该分组");
                }
                sysNoticeGroupMemberMapper.deleteSysNoticeGroupMemberById(mid);
            }
        } catch (Exception e) {
            throw new ServiceException(500, "分组成员移除失败", e.getMessage());
        }
        return true;
    }

    private void addMembersInternal(Long groupId, List<SysNoticeGroupMember> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        for (SysNoticeGroupMember m : members) {
            m.setGroupId(groupId);
        }
        try {
            sysNoticeGroupMemberMapper.insertSysNoticeGroupMember(members);
        } catch (Exception e) {
            throw new ServiceException(500, "分组成员添加失败", e.getMessage());
        }
    }
}
