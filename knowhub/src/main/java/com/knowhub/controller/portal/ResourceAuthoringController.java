package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.resource.quarry.ResourceQuarry;
import com.knowhub.pojo.resource.vo.ResourcePortalVo;
import com.knowhub.pojo.resource.vo.ResourceVo;
import com.knowhub.service.resource.impl.ResourcePortalService;
import com.knowhub.service.resource.impl.ResourceService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台资源创作与互动接口（/authoring/resource/**，走 /authoring/** authenticated 兜底，不进 /portal/ permitAll）。
 * <p>
 * 读走 /portal/** permitAll、写走 /authoring/** authenticated（决策#10 读写物理隔离，照 ArticleAuthoringController）。
 * 复用后台 ResourceService（不重写业务逻辑）：add/edit/publish/revoke/toggleLike/toggleCollect/rate/download
 * 八个写接口 + 互动接口均直接转发；仅 listMyResources / getResourceForAuthor / listMyCollected 三个薄方法
 * 在 ResourceService / ResourcePortalService 新增（见各 service 注释）。
 * <p>
 * 状态机由 ResourceServiceImpl 现有逻辑兜底：PUBLISHED 禁编辑（须先撤回）、PENDING_REVIEW 禁编辑；
 * 已发布换源须先 revoke 再 edit——后端已挡，前端按 status 隐藏"换文件"入口。
 * 资源无按钮权限键（无 level 等级、无 review 按钮到前台）：登录即可创作自己的资源 + 互动任意已发布资源。
 */
@Tag(name = "资源创作与互动", description = "前台资源创作：存草稿/编辑/发布/撤回/列表/下载 + 点赞/收藏/评分/我的收藏")
@RestController
@RequestMapping("/authoring/resource")
public class ResourceAuthoringController {

    @Autowired
    ResourceService resourceService;

    @Autowired
    ResourcePortalService resourcePortalService;

    // ============================ 创作 ============================

    @GetMapping("/list")
    @Operation(summary = "前台我的资源列表（薄封装 listMyResources，service 内硬置 authorId=当前用户）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ResourceVo>> myList(ResourceQuarry quarry) {
        // 复用后台列表 VO（含 status/reviewStatus 草稿/待审/驳回态，便于个人中心展示全态）。
        // service 内硬置 authorId=当前用户，仅返回作者本人资源全态；前端可传 status 过滤。
        PageInfo<ResourceVo> page = resourceService.listMyResources(quarry);
        return Result.success(page);
    }

    @GetMapping("/{resourceId}")
    @Operation(summary = "前台编辑回填（getResourceForAuthor，归属校验拒非作者，回填互动+下载链接）")
    @PreAuthorize("isAuthenticated()")
    public Result<ResourceVo> getForEdit(@PathVariable Long resourceId) {
        // 先归属校验（拒别人草稿），再复用 getResourceInfo 回填。副作用会 recordView 计一次浏览——
        // 作者编辑自己草稿误计一次影响可忽略。前端回填表单只用编辑相关字段，多余互动字段忽略。
        ResourceVo vo = resourceService.getResourceForAuthor(resourceId);
        return Result.success(vo);
    }

    @PostMapping
    @Operation(summary = "前台新建资源草稿（复用 addResourceInfo，作者=current user，DRAFT）")
    @Log(title = "资源创作", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> draft(@RequestBody ResourceVo vo) {
        Boolean b = resourceService.addResourceInfo(vo);
        return Result.success(b);
    }

    @PutMapping
    @Operation(summary = "前台编辑资源（复用 editResourceInfo，校验归属+状态机；PUBLISHED 须先撤回）")
    @Log(title = "资源创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> edit(@RequestBody ResourceVo vo) {
        Boolean b = resourceService.editResourceInfo(vo);
        return Result.success(b);
    }

    @PutMapping("/{resourceId}/publish")
    @Operation(summary = "前台发布资源（复用 publishResource，按审核开关决定 PUBLISHED 或 PENDING_REVIEW）")
    @Log(title = "资源创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> publish(@PathVariable Long resourceId) {
        Boolean b = resourceService.publishResource(resourceId);
        return Result.success(b);
    }

    @PutMapping("/{resourceId}/revoke")
    @Operation(summary = "前台撤回资源（复用 revokeResource → REVOKED，撤回后可再编辑/换源/再发布）")
    @Log(title = "资源创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> revoke(@PathVariable Long resourceId) {
        Boolean b = resourceService.revokeResource(resourceId);
        return Result.success(b);
    }

    // ============================ 下载 ============================

    @GetMapping("/{resourceId}/download")
    @Operation(summary = "FILE 资源下载链接下发（复用 downloadResource，校验 PUBLISHED+FILE，下载量 +1）")
    @PreAuthorize("isAuthenticated()")
    public Result<String> download(@PathVariable Long resourceId) {
        String url = resourceService.downloadResource(resourceId);
        return Result.success(url);
    }

    // ============================ 互动 ============================

    @PutMapping("/{resourceId}/like")
    @Operation(summary = "点赞/取消点赞资源（liked=true 点赞, false 取消，事实表 upsert，计数读时聚合）")
    @Log(title = "资源点赞", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleLike(@PathVariable Long resourceId,
                                       @RequestParam(required = false, defaultValue = "true") Boolean liked) {
        Boolean b = resourceService.toggleLike(resourceId, liked);
        return Result.success(b);
    }

    @PutMapping("/{resourceId}/collect")
    @Operation(summary = "收藏/取消收藏资源（collected=true 收藏, false 取消，事实表 upsert）")
    @Log(title = "资源收藏", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleCollect(@PathVariable Long resourceId,
                                          @RequestParam(required = false, defaultValue = "true") Boolean collected) {
        Boolean b = resourceService.toggleCollect(resourceId, collected);
        return Result.success(b);
    }

    @PutMapping("/{resourceId}/rating")
    @Operation(summary = "资源评分(1-5)（一人一资源一条，upsert 事实表，均值读时聚合）")
    @Log(title = "资源评分", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> rate(@PathVariable Long resourceId,
                                 @RequestParam Integer score) {
        Boolean b = resourceService.rateResource(resourceId, score);
        return Result.success(b);
    }

    @GetMapping("/collect/list")
    @Operation(summary = "我的资源收藏列表（按收藏时间倒序，仅返回前台可见口径的已发布资源）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ResourcePortalVo>> myCollected(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageInfo<ResourcePortalVo> page = resourcePortalService.listMyCollected(pageNum, pageSize);
        return Result.success(page);
    }
}