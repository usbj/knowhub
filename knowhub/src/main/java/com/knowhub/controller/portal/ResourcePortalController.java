package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.resource.quarry.ResourcePortalSearchQuarry;
import com.knowhub.pojo.resource.vo.ResourceCategoryTreeVo;
import com.knowhub.pojo.resource.vo.ResourcePortalDetailVo;
import com.knowhub.pojo.resource.vo.ResourcePortalVo;
import com.knowhub.service.resource.impl.ResourceCategoryService;
import com.knowhub.service.resource.impl.ResourcePortalService;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台资源门户公开接口（/portal/resource/*，全部无 @PreAuthorize，走 /portal/** permitAll）。
 * 铁律：所有 SQL 一律 status=PUBLISHED AND deleted=0（资源无 level 等级概念、无 review_status 前台过滤，
 * 与博客门户差异点：无 userViewLevel 透传、无越级锁态；非 PUBLISHED 资源前台根本不下发）。
 * 资源无标签体系，故无 /tag 相关接口（与博客门户差异点）；分类树复用 ResourceCategoryService.categoryTree()。
 */
@Tag(name = "资源门户", description = "前台公开搜索/推荐/详情/相关推荐/分类树")
@RestController
@RequestMapping("/portal/resource")
public class ResourcePortalController {

    @Autowired
    ResourcePortalService resourcePortalService;

    @Autowired
    ResourceCategoryService resourceCategoryService;

    @GetMapping("/search")
    @Operation(summary = "前台资源搜索（全文关键字 + 类型/分类复合过滤 + 排序，分页）")
    public Result<PageInfo<ResourcePortalVo>> search(ResourcePortalSearchQuarry quarry) {
        PageInfo<ResourcePortalVo> page = resourcePortalService.search(quarry);
        return Result.success(page);
    }

    @GetMapping("/recommend")
    @Operation(summary = "前台资源推荐 feed（全局热门兜底，无用户偏好源；详情页相关推荐排除当前）")
    public Result<List<ResourcePortalVo>> recommend(
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long excludeResourceId) {
        List<ResourcePortalVo> list = resourcePortalService.recommend(size, excludeResourceId);
        return Result.success(list);
    }

    @GetMapping("/{resourceId}")
    @Operation(summary = "前台资源详情（登录态回填互动态 + FILE 下载链接 + 登录态计浏览量）")
    public Result<ResourcePortalDetailVo> getDetail(@PathVariable Long resourceId) {
        ResourcePortalDetailVo vo = resourcePortalService.getDetail(resourceId);
        if (vo == null) {
            // 非 PUBLISHED 或不存在：前台 404 语义（用 500 状态 + 业务码 404 表达，与博客门户详情一致口径）
            return Result.error(404, "资源不存在或已下架");
        }
        return Result.success(vo);
    }

    @GetMapping("/{resourceId}/related")
    @Operation(summary = "详情页相关推荐（同 resource_category_id 排除自身，按热度排）")
    public Result<List<ResourcePortalVo>> related(@PathVariable Long resourceId,
                                                  @RequestParam(defaultValue = "10") int size) {
        List<ResourcePortalVo> list = resourcePortalService.related(resourceId, size);
        return Result.success(list);
    }

    @GetMapping("/category/tree")
    @Operation(summary = "资源分类树（前台列表分类筛选 + 上传表单分类选择数据源）")
    public Result<List<ResourceCategoryTreeVo>> categoryTree() {
        return Result.success(resourceCategoryService.categoryTree());
    }
}
