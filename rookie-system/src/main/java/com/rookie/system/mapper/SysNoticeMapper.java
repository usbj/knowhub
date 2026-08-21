package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysNotice;
import com.rookie.system.pojo.quarry.NoticeQuarry;
import com.rookie.system.pojo.vo.NoticeTypeCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysNoticeMapper {

    List<SysNotice> quarrySysNotice(NoticeQuarry quarry);

    Boolean addSysNotice(SysNotice sysNotice);

    Boolean editSysNoticeInfo(SysNotice sysNotice);

    Boolean deleteSysNoticeById(Long noticeId);

    SysNotice getSysNoticeInfoById(Long noticeId);

    Boolean softDeleteSysNotice(Long noticeId);

    /**
     * 查询当前用户可见的通知（按 is_top / publish_time / notice_id 排序）。
     * 可见范围：群发(ALL) / 分组(group_rel) / 指定成员(user_rel)。
     *
     * @param userId     当前用户 id
     * @param noticeType 可选通知类型过滤（sys_notice_type 字典 code：NOTICE/NOTIFY/REMIND），
     *                   null/空 表示不按类型过滤，返回全部可见通知。供前台 /notices 页类型筛选 tab 复用。
     */
    List<SysNotice> getNoticesForUser(@Param("userId") Long userId, @Param("noticeType") String noticeType);

    /**
     * 统计当前用户可见且未读的通知数（懒加载后铃铛徽标独立计数，不能依赖已加载分页列表）。
     * 可见范围与 getNoticesForUser 一致（ALL / 分组 / 指定成员），未读 = 无 read_time 记录。
     */
    Long countUnreadNoticesForUser(Long userId);

    /**
     * 全部已读：批量插入当前用户所有可见且未读通知的 sys_notice_read 记录（read_time=now()）。
     * 可见范围与未读判定与 {@link #countUnreadNoticesForUser(Long)} 一致，INSERT...SELECT 单 SQL 完成，
     * 覆盖懒加载下拉未加载页的未读（前端逐条调 read/{id} 只能覆盖已加载页，未加载页未读无法清零）。
     *
     * @param userId 当前用户 id
     * @return 受影响行数（实际新插入的已读记录数，0 表示本就全部已读）
     */
    int markAllReadForUser(Long userId);

    /**
     * 按 notice_type 分组统计当前用户可见的通知数，每行返回 {noticeType, count}。
     * 可见范围与 {@link #getNoticesForUser(Long, String)} 一致（ALL / 分组 / 指定成员），
     * 仅 status='PUBLISHED' 且未软删的通知。一次 GROUP BY 出所有类型计数，供前台分类 tab 角标真实总数用
     * （不传 noticeType 参数，避免前端为拿四个角标发四次分页请求）。
     *
     * @param userId 当前用户 id
     * @return 各通知类型的计数行（NOTICE/NOTIFY/REMIND 等实际存在的类型），无可见通知时返空列表
     */
    List<NoticeTypeCount> countMyNoticesByType(@Param("userId") Long userId);
}
