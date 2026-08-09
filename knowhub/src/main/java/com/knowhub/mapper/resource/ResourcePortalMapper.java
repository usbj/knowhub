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
 * 铁律：所有 SQL 一律 where r.deleted=0 and r.status='PUBLISHED'（资源无 level 等级概念，
 * 与博客 BlogPortalMapper 的差异点：无 userViewLevel 透传；非 PUBLISHED 资源永不下发前台）。
 * 列表类 SQL 不 select description 大字段。
 * 互动计数（点赞/收藏/评分）资源主表刻意不冗余，列表 SQL 用 inline 子查询回填（资源量级可接受）。
 */
@Mapper
public interface ResourcePortalMapper {

    /**
     * 搜索：全文关键字 + 资源类型 + 分类复合过滤 + 排序，分页由 PageHelper 接管。
     * keyword 命中 ft_resource_title_summary_desc 全文索引；不 select description。
     */
    List<ResourcePortalVo> searchResources(ResourcePortalSearchQuarry quarry);

    /**
     * 全局热门兜底（推荐 feed）：按 (download_count*3 + view_count + like_count*2 + collect_count*2) desc,
     * publish_time desc，排除 excludeResourceId 与 excludeIds，limit size。
     */
    List<ResourcePortalVo> recommendHot(@Param("excludeResourceId") Long excludeResourceId,
                                        @Param("excludeIds") List<Long> excludeIds,
                                        @Param("size") int size);

    /**
     * 详情：按主键取未删且已发布资源，含 description 大字段 + join 带出展示字段。
     * 非 PUBLISHED（草稿/待审/驳回/撤回）返回 null（controller 404）。
     */
    ResourcePortalDetailVo getPortalResourceDetail(@Param("resourceId") Long resourceId);

    /**
     * 相关推荐：同 resource_category_id 其它公开资源（排除自身），按热度排 limit size。
     * -1=其他类时同分类语义弱，SQL 内退化为"按热度全量"补足（service 不需特判）。
     */
    List<ResourcePortalVo> relatedResources(@Param("resourceId") Long resourceId,
                                            @Param("size") int size);
}