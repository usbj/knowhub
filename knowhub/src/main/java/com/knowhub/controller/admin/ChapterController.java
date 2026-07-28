package com.knowhub.controller.admin;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.article.quarry.ChapterQuarry;
import com.knowhub.pojo.article.vo.ChapterReviewLogVo;
import com.knowhub.pojo.article.vo.ChapterReviewVo;
import com.knowhub.pojo.article.vo.ChapterVo;
import com.knowhub.service.article.impl.ChapterService;
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
 * 章节管理接口（章节 ≈ 博客，正文走主表不分表）。
 * 章节是文章的子模块，无独立菜单页——从文章管理列表点"章节"跳到 /article/:articleId/chapters 二级路由页。
 * 权限键：按钮(非等级) knowhub:chapter:动作 走 @PreAuthorize（章节按钮权限键挂文章菜单下作隐形 menu_type=3）；
 * 章节编辑/审核的实际可见性由 service 层按章节可见性=文章可见性 + 提交者/作者归属判定，@PreAuthorize 仅进页面门槛。
 * 章节提交状态机由文章 visibility + 提交者是否文章作者决定（见 ChapterServiceImpl）；
 * 章节作者审核（仅 SEMIPUBLIC）走 chapter_review_log，不受系统审核开关影响。
 */
@Tag(name = "章节管理", description = "章节 CRUD / 提交发布 / 作者审核相关接口（文章子模块，无独立菜单）")
@RestController
@RequestMapping("/chapter")
public class ChapterController {

    @Autowired
    private ChapterService chapterService;

    @GetMapping("/list")
    @Operation(summary = "获取章节列表(按 articleId 过滤)")
    @PreAuthorize("hasAuthority('knowhub:chapter:quarry')")
    public Result<PageInfo<ChapterVo>> quarryChapter(ChapterQuarry quarry) {
        PageInfo<ChapterVo> pageInfo = chapterService.quarryChapter(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{chapterId}")
    @Operation(summary = "获取章节详情(含正文 content)")
    @PreAuthorize("hasAuthority('knowhub:chapter:info')")
    public Result<ChapterVo> getChapterInfo(@PathVariable Long chapterId) {
        ChapterVo vo = chapterService.getChapterInfo(chapterId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "新增/提交章节(按 visibility+提交者是否作者决定状态机分支)")
    @Log(title = "章节管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:chapter:add')")
    public Result<Boolean> submitChapter(@RequestBody ChapterVo vo) {
        Boolean b = chapterService.submitChapter(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑章节(PUBLISHED 禁编须先撤回)")
    @Log(title = "章节管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:chapter:add')")
    public Result<Boolean> editChapter(@RequestBody ChapterVo vo) {
        Boolean b = chapterService.editChapterInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{chapterIds}")
    @Operation(summary = "批量删除章节")
    @Log(title = "章节管理", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:chapter:delete')")
    public Result<Boolean> deleteChapter(@PathVariable Long[] chapterIds) {
        Boolean b = chapterService.deleteChapterInfo(chapterIds);
        return Result.success(b);
    }

    @PutMapping("/publish/{chapterId}")
    @Operation(summary = "提交/发布章节(DRAFT/REJECTED/REVOKED 再提交,按 visibility 决定走不走作者审)")
    @Log(title = "章节管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:chapter:publish')")
    public Result<Boolean> publishChapter(@PathVariable Long chapterId) {
        Boolean b = chapterService.publishChapter(chapterId);
        return Result.success(b);
    }

    @PutMapping("/revoke/{chapterId}")
    @Operation(summary = "撤回章节(仅 PUBLISHED 可撤回)")
    @Log(title = "章节管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:chapter:revoke')")
    public Result<Boolean> revokeChapter(@PathVariable Long chapterId) {
        Boolean b = chapterService.revokeChapter(chapterId);
        return Result.success(b);
    }

    @PutMapping("/review")
    @Operation(summary = "章节作者审核(仅 PENDING_AUTHOR_REVIEW 可审,文章作者审非作者提交的章节)")
    @Log(title = "章节管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:chapter:review')")
    public Result<Boolean> reviewChapter(@RequestBody ChapterReviewVo vo) {
        Boolean b = chapterService.reviewChapter(vo);
        return Result.success(b);
    }

    @GetMapping("/review-log/{chapterId}")
    @Operation(summary = "获取章节审核历史")
    @PreAuthorize("hasAuthority('knowhub:chapter:reviewLog')")
    public Result<List<ChapterReviewLogVo>> listReviewLog(@PathVariable Long chapterId) {
        List<ChapterReviewLogVo> list = chapterService.listReviewLog(chapterId);
        return Result.success(list);
    }
}
