package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.blog.quarry.BlogQuarry;
import com.knowhub.pojo.blog.vo.BlogPortalVo;
import com.knowhub.pojo.blog.vo.BlogVo;
import com.knowhub.service.blog.impl.BlogPortalService;
import com.knowhub.service.blog.impl.BlogService;
import com.knowhub.support.BlogPermissionResolver;
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
 * 前台作者创作接口（/authoring/blog/**，走 /authoring/** authenticated 兜底，不进 /portal/ permitAll）。
 * <p>
 * 读走 /portal/** permitAll、写走 /authoring/** authenticated（决策#10 读写物理隔离）。
 * 复用后台 BlogService（addBlogInfo/publishBlog/editBlogInfo/revokeBlog/toggleLike/toggleCollect），不重复实现业务逻辑；
 * addBlogInfo 内含分级创作闸（决策#11，前后台共用）。无按钮权限键——登录即可创作自己的内容。
 * 点赞/收藏在此 controller（PUT /authoring/blog/{id}/like|collect）：后台管理用不到，挪到前台与文章范式对齐。
 */
@Tag(name = "博客创作", description = "前台作者写博客：存草稿/发布/编辑/撤回/点赞收藏")
@RestController
@RequestMapping("/authoring/blog")
public class BlogAuthoringController {

    @Autowired
    BlogService blogService;

    @Autowired
    BlogPortalService blogPortalService;

    @GetMapping("/level")
    @Operation(summary = "当前用户博客 view 等级（创作页等级选择器权限感知，0/1/2/3）")
    @PreAuthorize("isAuthenticated()")
    public Result<Integer> myLevel() {
        // 纯内存计算：扫描当前登录用户 perms 取 view 最高等级（admin 自然 3，未授权 0）。
        // 前端据此禁用不可选等级（L1 用户只公开，L2 可选公开/内部，L3 全开），后端 assertCanCreateLevel 兜底。
        return Result.success(BlogPermissionResolver.resolve().view());
    }

    @GetMapping("/list")
    @Operation(summary = "前台我的博客列表（薄封装 quarryBlog，service 内回填 userId 走 author_id 分支）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<BlogVo>> myList(BlogQuarry quarry) {
        // 复用后台 quarryBlog：service 内已回填当前用户 userId 走"author_id=userId OR level<=userViewLevel"
        // 权限分支——前台登录用户调它天然只返回"自己写的 + 有权看的"。前端可传 status 过滤草稿/已发布。
        // 无按钮权限键，登录即可看自己的作品列表。
        PageInfo<BlogVo> page = blogService.quarryBlog(quarry);
        return Result.success(page);
    }

    @GetMapping("/{blogId}")
    @Operation(summary = "前台编辑回填（复用 getBlogInfo，canOp 已防越权：作者看自己全态、别人草稿拒）")
    @PreAuthorize("isAuthenticated()")
    public Result<BlogVo> getForEdit(@PathVariable Long blogId) {
        // 复用后台 getBlogInfo：内部 canOp(view) 校验防越权遍历（作者能看自己的、别人草稿拒），
        // 并回填 tagIds/tagNames/canEdit/isAuthor。副作用是会 recordView 计一次浏览量——
        // 作者编辑自己草稿误计一次影响可忽略（草稿本无他人看）。前端回填表单只用编辑相关字段，多余字段忽略。
        BlogVo vo = blogService.getBlogInfo(blogId);
        return Result.success(vo);
    }

    @PostMapping("/draft")
    @Operation(summary = "前台新建博客草稿（复用 addBlogInfo，含分级创作闸）")
    @Log(title = "博客创作", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> draft(@RequestBody BlogVo vo) {
        Boolean b = blogService.addBlogInfo(vo);
        return Result.success(b);
    }

    @PutMapping("/{blogId}/publish")
    @Operation(summary = "前台发布博客（复用 publishBlog，走审核开关）")
    @Log(title = "博客创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> publish(@PathVariable Long blogId) {
        Boolean b = blogService.publishBlog(blogId);
        return Result.success(b);
    }

    @PutMapping
    @Operation(summary = "前台编辑博客（复用 editBlogInfo，校验归属+先删后插标签）")
    @Log(title = "博客创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> edit(@RequestBody BlogVo vo) {
        Boolean b = blogService.editBlogInfo(vo);
        return Result.success(b);
    }

    @PutMapping("/{blogId}/revoke")
    @Operation(summary = "前台撤回博客（复用 revokeBlog）")
    @Log(title = "博客创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> revoke(@PathVariable Long blogId) {
        Boolean b = blogService.revokeBlog(blogId);
        return Result.success(b);
    }

    @PutMapping("/{blogId}/like")
    @Operation(summary = "点赞/取消点赞博客（liked=true 点赞, false 取消）")
    @Log(title = "博客点赞", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleLike(@PathVariable Long blogId,
                                      @RequestParam(required = false, defaultValue = "true") Boolean liked) {
        Boolean b = blogService.toggleLike(blogId, liked);
        return Result.success(b);
    }

    @PutMapping("/{blogId}/collect")
    @Operation(summary = "收藏/取消收藏博客（collected=true 收藏, false 取消）")
    @Log(title = "博客收藏", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleCollect(@PathVariable Long blogId,
                                          @RequestParam(required = false, defaultValue = "true") Boolean collected) {
        Boolean b = blogService.toggleCollect(blogId, collected);
        return Result.success(b);
    }

    @GetMapping("/collect/list")
    @Operation(summary = "我的博客收藏列表（按收藏时间倒序，仅前台可见口径的已发布博客）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<BlogPortalVo>> myCollected(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageInfo<BlogPortalVo> page = blogPortalService.listMyCollected(pageNum, pageSize);
        return Result.success(page);
    }
}
