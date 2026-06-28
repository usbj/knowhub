package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysNotice;
import com.rookie.system.pojo.quarry.NoticeQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysNoticeMapper {

    List<SysNotice> quarrySysNotice(NoticeQuarry quarry);

    Boolean addSysNotice(SysNotice sysNotice);

    Boolean editSysNoticeInfo(SysNotice sysNotice);

    Boolean deleteSysNoticeById(Long noticeId);

    SysNotice getSysNoticeInfoById(Long noticeId);

    Boolean softDeleteSysNotice(Long noticeId);

    List<SysNotice> getNoticesForUser(Long userId);
}
