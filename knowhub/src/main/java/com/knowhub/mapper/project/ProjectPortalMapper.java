package com.knowhub.mapper.project;

import com.knowhub.pojo.project.quarry.ProjectPortalSearchQuarry;
import com.knowhub.pojo.project.vo.ProjectPortalDetailVo;
import com.knowhub.pojo.project.vo.ProjectPortalVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 前台项目门户 Mapper（/portal/project/* 五读接口）。
 * <p>
 * 铁律：所有 SQL 一律 where deleted=0 and status='PUBLISHED' and level &lt;= #{userViewLevel}
 * （userViewLevel 由 service 注入：分级开关关恒 1，开取 ProjectPermissionResolver.view，未登录=1）。
 * L2/L3 永不下发前台（开关关时）。列表类 SQL 不 select description 大字段。
 * 项目无标签体系，故无按 tag 召回/标签聚合方法（项目推荐走全局热门+时间衰减兜底，搜索走 keyword/type）。
 *
 * @author knowhub
 */
@Mapper
public interface ProjectPortalMapper {

    /**
     * 搜索：标题 LIKE + type + level 过滤 + 排序，分页由 PageHelper 接管。
     * 排序：HOT 走推荐打分公式（download*3+like*2+view*1+时间衰减），LATEST/其它走 publish_time desc。
     */
    List<ProjectPortalVo> searchProjects(ProjectPortalSearchQuarry quarry);

    /**
     * 推荐：全局热门兜底（项目无标签无偏好召回；已浏览项目排除 excludeViewedProjectIds）。
     * 打分公式：download_count*3 + like_count*2 + collect_count*1 + view_count*1 + 时间衰减
     * （时间衰减在 SQL 内按 publish_time 距今天数 /30 -1，与博客 service 层算分等价，但本项目在 SQL 内算更简）。
     */
    List<ProjectPortalVo> recommendHot(@Param("userViewLevel") Integer userViewLevel,
                                        @Param("excludeProjectId") Long excludeProjectId,
                                        @Param("excludeViewedProjectIds") List<Long> excludeViewedProjectIds,
                                        @Param("size") int size);

    /**
     * 按 ID 集合取前台可见项目 VO（前台铁律过滤：deleted=0 AND status='PUBLISHED' AND level<=userViewLevel）。
     * 供"我的收藏"列表用：service 传收藏项目 ID 集，取"收藏 ∩ 前台可见"全量 VO，再按收藏时间倒序排。
     */
    List<ProjectPortalVo> listByIds(@Param("userViewLevel") Integer userViewLevel,
                                     @Param("projectIds") List<Long> projectIds);

    /**
     * 详情元数据（含 level + 各计数，不含 description 大字段；无 level 过滤——service 据此判越级锁态）。
     * 加 status='PUBLISHED' 约束避免泄草稿/驳回态（service 锁态后再按等级限正文）。
     */
    ProjectPortalDetailVo getPortalProjectMeta(@Param("projectId") Long projectId);

    /** 取详细介绍正文（仅 service 判定达权后调用，避免越级时白拉 mediumtext） */
    String getProjectDescription(@Param("projectId") Long projectId);

    /** 相关推荐（同 type 排除自身，按推荐打分 desc；level<=userViewLevel） */
    List<ProjectPortalVo> relatedProjects(@Param("projectId") Long projectId,
                                          @Param("userViewLevel") Integer userViewLevel,
                                          @Param("size") int size);

    /**
     * 登录用户浏览过的项目 ID（推荐召回时排除已浏览，防看点重复；未登录由 service 判 null 返回空）。
     * 实际查 user_view_history biz_type='PROJECT' AND biz_id=projectId。
     */
    List<Long> viewedProjectIdsByUser(@Param("userId") Long userId);

    /** 下载计数 +delta（前台文件下载成功下发后调；admin downloadFile 同口径，复用 project 表 download_count） */
    Boolean incrDownloadCount(@Param("projectId") Long projectId, @Param("delta") int delta);
}