package com.rookie.system.mapper;

import com.rookie.system.pojo.SysNoticeRead;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysNoticeReadMapper {

    Boolean addSysNoticeRead(SysNoticeRead sysNoticeRead);

    Boolean editSysNoticeReadInfo(SysNoticeRead sysNoticeRead);

    Boolean deleteSysNoticeReadById(Long id);

    Boolean deleteSysNoticeReadByNoticeId(Long noticeId);

    SysNoticeRead getSysNoticeReadInfoById(Long id);

    List<SysNoticeRead> getSysNoticeReadByNoticeId(Long noticeId);

    List<SysNoticeRead> getSysNoticeReadByUserId(Long userId);

    SysNoticeRead getByNoticeAndUser(Long noticeId, Long userId);
}
