package com.rookie.system.mapper;

import com.rookie.system.pojo.SysNoticeGroupRel;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysNoticeGroupRelMapper {

    Boolean addSysNoticeGroupRel(SysNoticeGroupRel sysNoticeGroupRel);

    Boolean insertSysNoticeGroupRel(List<SysNoticeGroupRel> sysNoticeGroupRels);

    Boolean deleteSysNoticeGroupRelById(Long id);

    Boolean deleteSysNoticeGroupRelByNoticeId(Long noticeId);

    Boolean deleteSysNoticeGroupRelByGroupId(Long groupId);

    SysNoticeGroupRel getSysNoticeGroupRelInfoById(Long id);

    List<SysNoticeGroupRel> getSysNoticeGroupRelByNoticeId(Long noticeId);

    List<SysNoticeGroupRel> getSysNoticeGroupRelByGroupId(Long groupId);
}
