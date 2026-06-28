package com.rookie.system.mapper;

import com.rookie.system.pojo.SysNoticeGroupMember;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysNoticeGroupMemberMapper {

    Boolean addSysNoticeGroupMember(SysNoticeGroupMember sysNoticeGroupMember);

    Boolean insertSysNoticeGroupMember(List<SysNoticeGroupMember> sysNoticeGroupMembers);

    Boolean deleteSysNoticeGroupMemberById(Long id);

    Boolean deleteSysNoticeGroupMemberByGroupId(Long groupId);

    SysNoticeGroupMember getSysNoticeGroupMemberInfoById(Long id);

    List<SysNoticeGroupMember> getSysNoticeGroupMemberByGroupId(Long groupId);
}
