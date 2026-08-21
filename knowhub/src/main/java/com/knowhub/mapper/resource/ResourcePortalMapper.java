package com.knowhub.mapper.resource;

import com.knowhub.pojo.resource.quarry.ResourcePortalSearchQuarry;
import com.knowhub.pojo.resource.vo.ResourcePortalDetailVo;
import com.knowhub.pojo.resource.vo.ResourcePortalVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 前台资源门户 Mapper（/portal/resource/* 读接口）。
 * <p>
 * 铁律：所有列表类 SQL 一律 where r.deleted=0 and r.status='PUBLISHED' and r.level &lt;= #{userViewLevel} + 1
 * （2026-08-18 权限大修引入 level 分级，搜索范围放宽一级，越级作品进列表带 locked 标记由 service 回填）。
 * 列表类 SQL 不 select description 大字段。
 * 互动计数（点赞/收藏/评分）资源主表刻意不冗余，列表 SQL 用 inline 子查询回填（资源量级可接受）。
 * <p>
 * 详情查询 getPortalResourceDetail 用独立 where（仅 deleted=0 AND status='PUBLISHED'，不带 level 过滤）——
 * service 取 meta 后判越级给锁态 VO（越级作品不下发下载链接/跳转 URL 但下发 description，"能搜到但锁"语义）。
 */
@Mapper
public interface ResourcePortalMapper {

    /**
     * 搜索：全文关键字 + 资源类型 + 分类复合过滤 + 排序，分页由 PageHelper 接管。
     * keyword 命中 ft_resource_title_summary_desc 全文索引；不 select description。
     * userViewLevel 由 service 透传（分级开关 + ResourcePermissionResolver），where 片段 level &lt;= userViewLevel + 1。
     */
    List<ResourcePortalVo> searchResources(ResourcePortalSearchQuarry quarry);

    /**
     * 全局热门兜底（推荐 feed）：按 (download_count*3 + view_count + like_count*2 + collect_count*2) desc,
     * publish_time desc，排除 excludeResourceId 与 excludeIds，limit size。
     * userViewLevel 透传控制 level 过滤范围（与 search 同口径 +1 放宽）。
     */
    List<ResourcePortalVo> recommendHot(@Param("excludeResourceId") Long excludeResourceId,
                                        @Param("excludeIds") List<Long> excludeIds,
                                        @Param("userViewLevel") Integer userViewLevel,
                                        @Param("size") int size);

    /**
     * 详情：按主键取未删且已发布资源，含 description 大字段 + join 带出展示字段。
     * 非 PUBLISHED（草稿/待审/驳回/撤回）返回 null（controller 404）。
     * 不带 level 过滤——service 取 meta 后判越级给锁态 VO（越级仍下发 description，锁下载/跳转）。
     */
    ResourcePortalDetailVo getPortalResourceDetail(@Param("resourceId") Long resourceId);

    /**
     * 按 ID 集合取前台可见资源 VO（前台铁律过滤：deleted=0 AND status='PUBLISHED' AND level &lt;= userViewLevel + 1）。
     * 供"我的收藏"列表用：service 传收藏 ID 集，取"收藏 ∩ 前台可见"全量 VO，再按收藏时间倒序排。
     * userViewLevel 透传控制 level 过滤范围（与 search 同口径 +1 放宽，越级收藏资源进列表带 locked）。
     */
    List<ResourcePortalVo> listByIds(@Param("resourceIds") List<Long> resourceIds,
                                     @Param("userViewLevel") Integer userViewLevel);

    /**
     * 相关推荐：同 resource_category_id 其它公开资源（排除自身），按热度排 limit size。
     * -1=其他类时同分类语义弱，SQL 内退化为"按热度全量"补足（service 不需特判）。
     * userViewLevel 透传控制 level 过滤范围（与 search 同口径 +1 放宽，越级相关资源进列表带 locked）。
     */
    List<ResourcePortalVo> relatedResources(@Param("resourceId") Long resourceId,
                                            @Param("userViewLevel") Integer userViewLevel,
                                            @Param("size") int size);
}