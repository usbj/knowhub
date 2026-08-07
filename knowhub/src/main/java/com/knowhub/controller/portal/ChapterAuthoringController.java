package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.article.quarry.ChapterQuarry;
import com.knowhub.pojo.article.vo.ChapterReviewLogVo;
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
 * 前台章节创作接口（/authoring/chapter/**，走 /authoring/** authenticated 兜底，不进 /portal/ permitAll）。
 * <p>
 * 读走 /portal/article/{id}/chapter/{chapterId} permitAll（2026-07-29 落地章节正文公开读）；
 * 写走 /authoring/chapter/** authenticated（决策#10 读写物理隔离）。
 * 复用后台 ChapterService（quarryChapter/getChapterInfo/submitChapter/editChapterInfo/publishChapter/revokeChapter
 * /deleteChapterInfo/listReviewLog），不重复实现业务逻辑——前台登录态 currentUser() 强转安全。
 * 章节不分等级、可见性随文章、无标签无封面，创作表单精简（章节名+排序+正文）。章节状态机由文章 visibility
 * + 提交者是否作者决定（详见 ChapterServiceImpl）。无按钮权限键——登录即可在自己有权的文章下提交/管理章节。
 * <p>
 * 章节审核（仅 SEMIPUBLIC 文章非作者提交）：前台文章作者可在此走 reviewChapter 审核，本控制器复用后台 reviewChapter。
 */
@Tag(name = "章节创作", description = "前台作者/编辑写章节：列表/编辑回填/提交/编辑/发布/撤回/删除/审核")
@RestController
@RequestMapping("/authoring/chapter")
public class ChapterAuthoringController {

    @Autowired
    ChapterService chapterService;

    @GetMapping("/list")
    @Operation(summary = "前台某文章的章节列表（薄封装 quarryChapter，需 articleId；service 校验能看该文章）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ChapterVo>> myList(ChapterQuarry quarry) {
        // 复用后台 quarryChapter：service 内校验能看该文章(canViewArticle) + 回填 articleAuthorId 供非PUBLISHED可见性判定。
        // 前台登录用户在自己文章下/有权看的文章下查看章节。无按钮权限键。
        PageInfo<ChapterVo> page = chapterService.quarryChapter(quarry);
        return Result.success(page);
    }

    @GetMapping("/{chapterId}")
    @Operation(summary = "前台章节编辑回填（复用 getChapterInfo，章节可见性=文章可见性防越权）")
    @PreAuthorize("isAuthenticated()")
    public Result<ChapterVo> getForEdit(@PathVariable Long chapterId) {
        // 复用后台 getChapterInfo：内部按章节可见性=文章可见性校验（PUBLISHED 章节能看文章即可看；
        // 非 PUBLISHED 仅章节作者/文章作者可见），回填 canEdit/canReview。副作用 recordView 计一次章节浏览量——
        // 作者编辑自己章节误计一次影响可忽略。前端表单只用创作相关字段（chapterName/sortOrder/content），多余忽略。
        ChapterVo vo = chapterService.getChapterInfo(chapterId);
        return Result.success(vo);
    }

    @PostMapping
    @Operation(summary = "前台提交新章节（复用 submitChapter，按文章 visibility 决定状态机）")
    @Log(title = "章节创作", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> submit(@RequestBody ChapterVo vo) {
        Boolean b = chapterService.submitChapter(vo);
        return Result.success(b);
    }

    @PutMapping
    @Operation(summary = "前台编辑章节（复用 editChapterInfo，PUBLISHED 须先撤回改）")
    @Log(title = "章节创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> edit(@RequestBody ChapterVo vo) {
        Boolean b = chapterService.editChapterInfo(vo);
        return Result.success(b);
    }

    @PutMapping("/{chapterId}/publish")
    @Operation(summary = "前台再次提交/发布章节（复用 publishChapter，用于 DRAFT/REJECTED/REVOKED 再提交）")
    @Log(title = "章节创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> publish(@PathVariable Long chapterId) {
        Boolean b = chapterService.publishChapter(chapterId);
        return Result.success(b);
    }

    @PutMapping("/{chapterId}/revoke")
    @Operation(summary = "前台撤回章节（复用 revokeChapter → REVOKED，可再编辑/再提交）")
    @Log(title = "章节创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> revoke(@PathVariable Long chapterId) {
        Boolean b = chapterService.revokeChapter(chapterId);
        return Result.success(b);
    }

    @PutMapping("/reorder")
    @Operation(summary = "前台批量重排章节顺序（长按拖拽持久化，body=[{chapterId,sortOrder,articleId}]，逐章 canEdit 校验）")
    @Log(title = "章节重排", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> reorder(@RequestBody List<ChapterVo> orders) {
        Boolean b = chapterService.reorderChapters(orders);
        return Result.success(b);
    }

    @DeleteMapping("/{chapterIds}")
    @Operation(summary = "前台删除章节（复用 deleteChapterInfo，章节作者 OR 文章作者 OR delete 权限）")
    @Log(title = "章节创作", businessType = BusinessType.DELETE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> delete(@PathVariable Long[] chapterIds) {
        Boolean b = chapterService.deleteChapterInfo(chapterIds);
        return Result.success(b);
    }

    @GetMapping("/review-log/{chapterId}")
    @Operation(summary = "前台章节审核历史（复用 listReviewLog，仅 SEMIPUBLIC 场景有记录）")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ChapterReviewLogVo>> reviewLog(@PathVariable Long chapterId) {
        List<ChapterReviewLogVo> list = chapterService.listReviewLog(chapterId);
        return Result.success(list);
    }
}