package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.article.quarry.ArticleQuarry;
import com.knowhub.pojo.article.vo.ArticlePortalVo;
import com.knowhub.pojo.article.vo.ArticleVo;
import com.knowhub.service.article.impl.ArticleContributorService;
import com.knowhub.service.article.impl.ArticlePortalService;
import com.knowhub.service.article.impl.ArticleService;
import com.knowhub.support.ArticlePermissionResolver;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.framework.security.pojo.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台作者/读者互动 + 创作接口（/authoring/article/**，走 /authoring/** authenticated 兜底，不进 /portal/ permitAll）。
 * <p>
 * 读走 /portal/** permitAll、写走 /authoring/** authenticated（决策#10 读写物理隔离，照 BlogAuthoringController）。
 * 两类职责合一：
 * - 互动（点赞/收藏 toggle + 我的收藏列表）：业务逻辑在 ArticlePortalService（事务内 upsert 事实表 + 主表冗余列同步），2026-07-29 落地。
 * - 创作（存草稿/发布/编辑/撤回/我的文章列表/编辑回填/view 等级）：复用后台 ArticleService，2026-07-29 文档学习前台对接补齐。
 * 文章无按钮权限键——登录即可互动/创作自己的内容。点赞/收藏计数同步走 ArticlePortalService。
 */
@Tag(name = "文章创作与互动", description = "前台文章点赞/收藏 + 创作：存草稿/发布/编辑/撤回/列表/章节管理")
@RestController
@RequestMapping("/authoring/article")
public class ArticleAuthoringController {

    @Autowired
    ArticleService articleService;

    @Autowired
    ArticlePortalService articlePortalService;

    @Autowired
    ArticleContributorService articleContributorService;

    // ============================ 创作 ============================

    @GetMapping("/level")
    @Operation(summary = "当前用户文章查看等级（创作页等级选择器权限感知，0/1/2/3）")
    @PreAuthorize("isAuthenticated()")
    public Result<Integer> myLevel() {
        // 纯内存计算：扫描当前登录用户 perms 取最高等级（admin 自然 3，未授权 0）。
        // 前端据此禁用不可选等级（L1 用户只能公开，L2 可选 L1/L2，L3 全开），后端 addArticleInfo 的 assertCanCreateLevel 兜底。
        // 2026-08-18 权限大修单键化：resolve().view() → resolve().level()（单键 knowhub:article:lN）。
        return Result.success(ArticlePermissionResolver.resolve().level());
    }

    @GetMapping("/list")
    @Operation(summary = "前台我的文章列表（薄封装 quarryArticle，controller 注入 authorId=当前用户 userId 收紧到本人创建）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ArticleVo>> myList(ArticleQuarry quarry) {
        // 强制只召回本人创建的文章：quarry.authorId = 当前用户 userId。
        // service 内 quarryArticle 会回填 userViewLevel/userId 走 "author_id=userId OR level<=userViewLevel"
        // OR 分支，叠加此 AND 后集合被 author_id=? 收紧到本人，OR 分支恒真叠加不放大，非本人创建即被排除。
        // 不改 service/XML（admin 共用 quarryArticle 保持原"参与/有权看"召回口径，不受影响）。
        quarry.setAuthorId(currentUserId());
        PageInfo<ArticleVo> page = articleService.quarryArticle(quarry);
        return Result.success(page);
    }

    /** 当前登录用户 userId（principal 是 UserInfo，/authoring/** 已 authenticated 兜底）。 */
    private Long currentUserId() {
        return ((UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId();
    }

    @GetMapping("/{articleId}")
    @Operation(summary = "前台编辑回填（复用 getArticleInfo，canOp 已防越权：作者看自己全态、别人草稿拒）")
    @PreAuthorize("isAuthenticated()")
    public Result<ArticleVo> getForEdit(@PathVariable Long articleId) {
        // 复用后台 getArticleInfo：内部 canOp(view) 校验防越权遍历（作者能看自己的、别人草稿拒），
        // 并回填 tagIds/tagNames/canView/canEdit/isAuthor。副作用是会 recordView 计一次浏览量——
        // 作者编辑自己草稿误计一次影响可忽略（草稿本无他人看）。前端回填表单只用编辑相关字段，多余字段忽略。
        ArticleVo vo = articleService.getArticleInfo(articleId);
        return Result.success(vo);
    }

    @PostMapping
    @Operation(summary = "前台新建文章草稿（复用 addArticleInfo，作者=current user，DRAFT）")
    @Log(title = "文章创作", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> draft(@RequestBody ArticleVo vo) {
        Boolean b = articleService.addArticleInfo(vo);
        return Result.success(b);
    }

    @PutMapping
    @Operation(summary = "前台编辑文章（复用 editArticleInfo，校验归属+状态机+先删后插标签）")
    @Log(title = "文章创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> edit(@RequestBody ArticleVo vo) {
        Boolean b = articleService.editArticleInfo(vo);
        return Result.success(b);
    }

    @PutMapping("/{articleId}/publish")
    @Operation(summary = "前台发布文章（复用 publishArticle，按审核开关决定 PUBLISHED 或 PENDING_REVIEW）")
    @Log(title = "文章创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> publish(@PathVariable Long articleId) {
        Boolean b = articleService.publishArticle(articleId);
        return Result.success(b);
    }

    @PutMapping("/{articleId}/revoke")
    @Operation(summary = "前台撤回文章（复用 revokeArticle → REVOKED，撤回后可再编辑/再发布）")
    @Log(title = "文章创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> revoke(@PathVariable Long articleId) {
        Boolean b = articleService.revokeArticle(articleId);
        return Result.success(b);
    }

    /**
     * 前台删除自己的文章（软删：article.deleted=1 + 级联软删章节 + 软删封面 file_object）。
     * 复用 ArticleService.deleteArticleInfo(Long[])，其内权限校验=作者 OR knowhub:article:delete 按钮权限，
     * 普通前台用户只能删自己写的（无 delete 按钮权限键）；admin 全权。单条包装 Long[]。
     */
    @DeleteMapping("/{articleId}")
    @Operation(summary = "前台删除文章（软删，仅作者或 admin；级联软删章节与封面）")
    @Log(title = "文章创作", businessType = BusinessType.DELETE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> delete(@PathVariable Long articleId) {
        Boolean b = articleService.deleteArticleInfo(new Long[]{articleId});
        return Result.success(b);
    }

    @PostMapping("/{articleId}/apply-contributor")
    @Operation(summary = "前台申请成为该文章贡献者（建一条 PENDING 申请并通知作者去协作页审批）")
    @Log(title = "文章贡献申请", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> applyContributor(@PathVariable Long articleId) {
        // service 内去重（已有 PENDING/APPROVED 的 ACTIVE 行则提示，REJECTED 行允许重申软删旧行再插新 PENDING）；
        // 防作者申请自己（作者天然能写自己文章的章节）。通知文章作者 routePath=/collaboration?tab=received-applications。
        Boolean b = articleContributorService.apply(articleId);
        return Result.success(b);
    }

    // ============================ 互动（2026-07-29 落地，创作补齐时合并到此类） ============================

    @PutMapping("/{articleId}/collect")
    @Operation(summary = "收藏/取消收藏文章（collected=true 收藏, false 取消，主表 collect_count 同步）")
    @Log(title = "文章收藏", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleCollect(@PathVariable Long articleId,
                                          @RequestParam(required = false, defaultValue = "true") Boolean collected) {
        Boolean b = articlePortalService.toggleCollect(articleId, collected);
        return Result.success(b);
    }

    @PutMapping("/{articleId}/like")
    @Operation(summary = "点赞/取消点赞文章（like=true 点赞, false 取消，主表 like_count 同步）")
    @Log(title = "文章点赞", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleLike(@PathVariable Long articleId,
                                       @RequestParam(required = false, defaultValue = "true") Boolean liked) {
        Boolean b = articlePortalService.toggleLike(articleId, liked);
        return Result.success(b);
    }

    @GetMapping("/collect/list")
    @Operation(summary = "我的文章收藏列表（按收藏时间倒序，仅返回前台可见口径的已发布文章）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ArticlePortalVo>> myCollected(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageInfo<ArticlePortalVo> page = articlePortalService.listMyCollected(pageNum, pageSize);
        return Result.success(page);
    }
}
