package com.knowhub.controller.admin;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.blog.quarry.BlogQuarry;
import com.knowhub.pojo.blog.vo.BlogVo;
import com.knowhub.pojo.common.vo.ReviewLogVo;
import com.knowhub.pojo.common.vo.ReviewVo;
import com.knowhub.service.blog.impl.BlogService;
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
 * 博客文章接口。
 * 模块本身即"博客(blog)"，文章实体直接称 Blog，权限键三段式 knowhub:blog:quarry/knowhub:blog:add/...，
 * 路由为 /blog；权限键与 sys_menu 中 knowhub:blog:* 行一致。
 */
@Tag(name = "博客文章", description = "博客文章 CRUD / 发布审核 / 点赞收藏相关接口")
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Autowired
    BlogService blogService;

    @GetMapping("/list")
    @Operation(summary = "获取博客列表")
    @PreAuthorize("hasAuthority('knowhub:blog:quarry')")
    public Result<PageInfo<BlogVo>> quarryBlog(BlogQuarry quarry) {
        PageInfo<BlogVo> pageInfo = blogService.quarryBlog(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{blogId}")
    @Operation(summary = "获取博客详情")
    @PreAuthorize("hasAuthority('knowhub:blog:info')")
    public Result<BlogVo> getBlogInfo(@PathVariable Long blogId) {
        BlogVo vo = blogService.getBlogInfo(blogId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "添加博客")
    @Log(title = "博客文章", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:blog:add')")
    public Result<Boolean> addBlog(@RequestBody BlogVo vo) {
        Boolean b = blogService.addBlogInfo(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑博客")
    @Log(title = "博客文章", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:blog:edit')")
    public Result<Boolean> editBlog(@RequestBody BlogVo vo) {
        Boolean b = blogService.editBlogInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{blogIds}")
    @Operation(summary = "批量删除博客")
    @Log(title = "博客文章", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:blog:delete')")
    public Result<Boolean> deleteBlog(@PathVariable Long[] blogIds) {
        Boolean b = blogService.deleteBlogInfo(blogIds);
        return Result.success(b);
    }

    @PutMapping("/publish/{blogId}")
    @Operation(summary = "发布博客")
    @Log(title = "博客文章", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:blog:publish')")
    public Result<Boolean> publishBlog(@PathVariable Long blogId) {
        Boolean b = blogService.publishBlog(blogId);
        return Result.success(b);
    }

    @PutMapping("/revoke/{blogId}")
    @Operation(summary = "撤回博客")
    @Log(title = "博客文章", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:blog:revoke')")
    public Result<Boolean> revokeBlog(@PathVariable Long blogId) {
        Boolean b = blogService.revokeBlog(blogId);
        return Result.success(b);
    }

    @PutMapping("/review")
    @Operation(summary = "审核博客")
    @Log(title = "博客文章", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:blog:review')")
    public Result<Boolean> reviewBlog(@RequestBody ReviewVo vo) {
        Boolean b = blogService.reviewBlog(vo);
        return Result.success(b);
    }

    @GetMapping("/review-log/{blogId}")
    @Operation(summary = "获取博客审核历史")
    @PreAuthorize("hasAuthority('knowhub:blog:info')")
    public Result<List<ReviewLogVo>> listReviewLog(@PathVariable Long blogId) {
        List<ReviewLogVo> list = blogService.listReviewLog(blogId);
        return Result.success(list);
    }

    @PutMapping("/like/{blogId}")
    @Operation(summary = "点赞/取消点赞")
    @Log(title = "博客文章", businessType = BusinessType.UPDATE)
    public Result<Boolean> toggleLike(@PathVariable Long blogId, @RequestParam Boolean liked) {
        Boolean b = blogService.toggleLike(blogId, liked);
        return Result.success(b);
    }

    @PutMapping("/collect/{blogId}")
    @Operation(summary = "收藏/取消收藏")
    @Log(title = "博客文章", businessType = BusinessType.UPDATE)
    public Result<Boolean> toggleCollect(@PathVariable Long blogId, @RequestParam Boolean collected) {
        Boolean b = blogService.toggleCollect(blogId, collected);
        return Result.success(b);
    }
}