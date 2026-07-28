package com.knowhub.controller;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.ResourceQuarry;
import com.knowhub.pojo.vo.ResourceReviewLogVo;
import com.knowhub.pojo.vo.ResourceReviewVo;
import com.knowhub.pojo.vo.ResourceVo;
import com.knowhub.service.ResourceService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源管理接口。
 * 后台菜单名"资源管理"，前台展示端待做叫"资源推荐"。
 * 权限键三段式 knowhub:resource:动作，路由 /resource（knowhub 命名空间，不套 /sys）。
 * 审核流程复用博客那套范式（状态机+回避+流水表+对账任务），互动计数走事实表聚合不冗余主表。
 */
@Tag(name = "资源管理", description = "资源 CRUD / 发布审核 / 点赞收藏评分 / 下载相关接口")
@RestController
@RequestMapping("/resource")
public class ResourceController {

    @Autowired
    ResourceService resourceService;

    @GetMapping("/list")
    @Operation(summary = "获取资源列表")
    @PreAuthorize("hasAuthority('knowhub:resource:quarry')")
    public Result<PageInfo<ResourceVo>> quarryResource(ResourceQuarry quarry) {
        PageInfo<ResourceVo> pageInfo = resourceService.quarryResource(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{resourceId}")
    @Operation(summary = "获取资源详情")
    @PreAuthorize("hasAuthority('knowhub:resource:info')")
    public Result<ResourceVo> getResourceInfo(@PathVariable Long resourceId) {
        ResourceVo vo = resourceService.getResourceInfo(resourceId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "添加资源")
    @Log(title = "资源管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:resource:add')")
    public Result<Boolean> addResource(@RequestBody ResourceVo vo) {
        Boolean b = resourceService.addResourceInfo(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑资源")
    @Log(title = "资源管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:resource:edit')")
    public Result<Boolean> editResource(@RequestBody ResourceVo vo) {
        Boolean b = resourceService.editResourceInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{resourceIds}")
    @Operation(summary = "批量删除资源")
    @Log(title = "资源管理", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:resource:delete')")
    public Result<Boolean> deleteResource(@PathVariable Long[] resourceIds) {
        Boolean b = resourceService.deleteResourceInfo(resourceIds);
        return Result.success(b);
    }

    @PutMapping("/publish/{resourceId}")
    @Operation(summary = "发布资源")
    @Log(title = "资源管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:resource:publish')")
    public Result<Boolean> publishResource(@PathVariable Long resourceId) {
        Boolean b = resourceService.publishResource(resourceId);
        return Result.success(b);
    }

    @PutMapping("/revoke/{resourceId}")
    @Operation(summary = "撤回资源")
    @Log(title = "资源管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:resource:revoke')")
    public Result<Boolean> revokeResource(@PathVariable Long resourceId) {
        Boolean b = resourceService.revokeResource(resourceId);
        return Result.success(b);
    }

    @PutMapping("/review")
    @Operation(summary = "审核资源")
    @Log(title = "资源管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:resource:review')")
    public Result<Boolean> reviewResource(@RequestBody ResourceReviewVo vo) {
        Boolean b = resourceService.reviewResource(vo);
        return Result.success(b);
    }

    @GetMapping("/review-log/{resourceId}")
    @Operation(summary = "获取资源审核历史")
    @PreAuthorize("hasAuthority('knowhub:resource:reviewLog')")
    public Result<List<ResourceReviewLogVo>> listReviewLog(@PathVariable Long resourceId) {
        List<ResourceReviewLogVo> list = resourceService.listReviewLog(resourceId);
        return Result.success(list);
    }

    @PutMapping("/like/{resourceId}")
    @Operation(summary = "点赞/取消点赞")
    @Log(title = "资源管理", businessType = BusinessType.UPDATE)
    public Result<Boolean> toggleLike(@PathVariable Long resourceId, @RequestParam Boolean liked) {
        Boolean b = resourceService.toggleLike(resourceId, liked);
        return Result.success(b);
    }

    @PutMapping("/collect/{resourceId}")
    @Operation(summary = "收藏/取消收藏")
    @Log(title = "资源管理", businessType = BusinessType.UPDATE)
    public Result<Boolean> toggleCollect(@PathVariable Long resourceId, @RequestParam Boolean collected) {
        Boolean b = resourceService.toggleCollect(resourceId, collected);
        return Result.success(b);
    }

    @PutMapping("/rating/{resourceId}")
    @Operation(summary = "评分(1-5)")
    @Log(title = "资源管理", businessType = BusinessType.UPDATE)
    public Result<Boolean> rateResource(@PathVariable Long resourceId, @RequestParam Integer score) {
        Boolean b = resourceService.rateResource(resourceId, score);
        return Result.success(b);
    }

    @GetMapping("/download/{resourceId}")
    @Operation(summary = "获取FILE资源下载链接")
    @PreAuthorize("hasAuthority('knowhub:resource:download')")
    public Result<String> downloadResource(@PathVariable Long resourceId) {
        String downloadUrl = resourceService.downloadResource(resourceId);
        return Result.success(downloadUrl);
    }
}