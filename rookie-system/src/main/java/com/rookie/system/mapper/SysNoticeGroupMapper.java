package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysNoticeGroup;
import com.rookie.system.pojo.quarry.NoticeGroupQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysNoticeGroupMapper {

    Boolean addSysNoticeGroup(SysNoticeGroup sysNoticeGroup);

    Boolean editSysNoticeGroupInfo(SysNoticeGroup sysNoticeGroup);

    Boolean deleteSysNoticeGroupById(Long groupId);

    SysNoticeGroup getSysNoticeGroupInfoById(Long groupId);

    List<SysNoticeGroup> quarrySysNoticeGroup(NoticeGroupQuarry quarry);
}
