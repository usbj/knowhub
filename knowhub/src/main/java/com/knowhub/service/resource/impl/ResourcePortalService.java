package com.knowhub.service.resource.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.resource.quarry.ResourcePortalSearchQuarry;
import com.knowhub.pojo.resource.vo.ResourcePortalDetailVo;
import com.knowhub.pojo.resource.vo.ResourcePortalVo;

import java.util.List;

/**
 * 前台资源门户 Service（/portal/resource/* 读接口的业务逻辑集中在此）。
 * 接口只暴露 DTO，不暴露实体。前台无 @PreAuthorize，登录态在 service 内防御性获取。
 * <p>
 * 资源无标签体系、无 level 等级概念（与博客 ArticlePortalService 的差异点）：
 * - 推荐 feed 退化为全局热门兜底（无用户偏好 tag 召回）；
 * - 无越级锁态，非 PUBLISHED 资源前台根本不下发（详情查不到返 null→controller 404）。
 */
public interface ResourcePortalService {

    /** 全文搜索+类型/分类复合过滤+排序，分页 */
    PageInfo<ResourcePortalVo> search(ResourcePortalSearchQuarry quarry);

    /**
     * 推荐 feed（资源无用户偏好源，等价热度榜口径）。
     * @param size 召回条数
     * @param excludeResourceId 排除的资源ID（详情页相关推荐时排除当前；feed 可为 null）
     */
    List<ResourcePortalVo> recommend(int size, Long excludeResourceId);

    /**
     * 前台公开详情。
     * 登录态回填当前用户互动态（hasLiked/hasCollected/myScore）+ FILE 下载链接（不计 download_count）；
     * 登录态计浏览量（未登录不计，对齐决策#3）。
     * 非 PUBLISHED 资源返回 null（controller 404 语义）。
     */
    ResourcePortalDetailVo getDetail(Long resourceId);

    /** 详情页相关推荐：同 resource_category_id 其它公开资源，按热度排 */
    List<ResourcePortalVo> related(Long resourceId, int size);

    /**
     * 前台"我的收藏"列表：取当前用户收藏的资源ID（按收藏时间倒序），用 recommendHot 同口径 SQL
     * 取"收藏 ∩ 前台可见(PUBLISHED)"的 VO，再按收藏顺序排。照 ArticlePortalService.listMyCollected 范式。
     * 未登录防御返空（authoring controller 已 isAuthenticated 兜底，此处双保险）。
     */
    PageInfo<ResourcePortalVo> listMyCollected(int pageNum, int pageSize);
}