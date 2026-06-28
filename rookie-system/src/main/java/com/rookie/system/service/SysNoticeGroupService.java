package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.system.pojo.quarry.NoticeGroupQuarry;
import com.rookie.system.pojo.vo.SysNoticeGroupVo;

import java.util.List;

public interface SysNoticeGroupService {

    PageInfo<SysNoticeGroupVo> quarrySysNoticeGroup(NoticeGroupQuarry quarry);

    SysNoticeGroupVo getSysNoticeGroupInfo(Long groupId);

    Boolean addSysNoticeGroupInfo(SysNoticeGroupVo vo);

    Boolean editSysNoticeGroupInfo(SysNoticeGroupVo vo);

    Boolean deleteSysNoticeGroupInfo(Long[] groupIds);

    Boolean addMembers(Long groupId, List<Long> userIds);

    Boolean removeMembers(Long groupId, List<Long> memberIds);
}
