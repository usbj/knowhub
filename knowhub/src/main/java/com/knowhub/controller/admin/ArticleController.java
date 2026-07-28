package com.knowhub.controller;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.ArticleQuarry;
import com.knowhub.pojo.vo.ArticleReviewLogVo;
import com.knowhub.pojo.vo.ArticleReviewVo;
import com.knowhub.pojo.vo.ArticleVo;
import com.knowhub.service.ArticleService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章管理接口。
 * 后台菜单名"文章管理"，文章=章节集合（参考 Vue/Element-Plus 官方文档站结构）。
 * 权限键：按钮(非等级) knowhub:article:动作 走 @PreAuthorize；
 * 等级(view/edit:lN) 走 service 层 ArticlePermissionResolver 扫 perms 取最高等级判定，
 * 列表 quarry 接口 @PreAuthorize 用 knowhub:article:quarry（进页面门槛），具体可见性由 SQL 过滤。
 * 审核流程复用博客/项目范式（状态机+回避+流水表+对账任务），ReviewAction 枚举复用。
 * 文章无成员表（轻量权限：系统级 + 作者归属），无 download 操作（文章无下载）。
 */
@Tag(name = "文章管理", description = "文章 CRUD / 发布审核 / 章节管理相关接口")
@RestController
@RequestMapping("/article")
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    @GetMapping("/list")
    @Operation(summary = "获取文章列表")
    @PreAuthorize("hasAuthority('knowhub:article:quarry')")
    public Result<PageInfo<ArticleVo>> quarryArticle(ArticleQuarry quarry) {
        PageInfo<ArticleVo> pageInfo = articleService.quarryArticle(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{articleId}")
    @Operation(summary = "获取文章详情")
    @PreAuthorize("hasAuthority('knowhub:article:info')")
    public Result<ArticleVo> getArticleInfo(@PathVariable Long articleId) {
        ArticleVo vo = articleService.getArticleInfo(articleId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "新增文章")
    @Log(title = "文章管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:article:add')")
    public Result<Boolean> addArticle(@RequestBody ArticleVo vo) {
        Boolean b = articleService.addArticleInfo(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑文章")
    @Log(title = "文章管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:article:add')")
    public Result<Boolean> editArticle(@RequestBody ArticleVo vo) {
        Boolean b = articleService.editArticleInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{articleIds}")
    @Operation(summary = "批量删除文章")
    @Log(title = "文章管理", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:article:delete')")
    public Result<Boolean> deleteArticle(@PathVariable Long[] articleIds) {
        Boolean b = articleService.deleteArticleInfo(articleIds);
        return Result.success(b);
    }

    @PutMapping("/publish/{articleId}")
    @Operation(summary = "发布文章")
    @Log(title = "文章管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:article:publish')")
    public Result<Boolean> publishArticle(@PathVariable Long articleId) {
        Boolean b = articleService.publishArticle(articleId);
        return Result.success(b);
    }

    @PutMapping("/revoke/{articleId}")
    @Operation(summary = "撤回文章")
    @Log(title = "文章管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:article:revoke')")
    public Result<Boolean> revokeArticle(@PathVariable Long articleId) {
        Boolean b = articleService.revokeArticle(articleId);
        return Result.success(b);
    }

    @PutMapping("/review")
    @Operation(summary = "审核文章")
    @Log(title = "文章管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:article:review')")
    public Result<Boolean> reviewArticle(@RequestBody ArticleReviewVo vo) {
        Boolean b = articleService.reviewArticle(vo);
        return Result.success(b);
    }

    @GetMapping("/review-log/{articleId}")
    @Operation(summary = "获取文章审核历史")
    @PreAuthorize("hasAuthority('knowhub:article:reviewLog')")
    public Result<List<ArticleReviewLogVo>> listReviewLog(@PathVariable Long articleId) {
        List<ArticleReviewLogVo> list = articleService.listReviewLog(articleId);
        return Result.success(list);
    }
}
