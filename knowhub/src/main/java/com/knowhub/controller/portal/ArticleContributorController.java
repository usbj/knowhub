package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.article.vo.ArticleContributorVo;
import com.knowhub.service.article.impl.ArticleContributorService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台文章贡献者申请/授权接口（/authoring/contributor/**，走 /authoring/** authenticated 兜底，无按钮权限键）。
 * <p>
 * 前台「我的协作」页「我收到的贡献申请 / 我申请过的贡献资格」两 tab 的后端。
 * - list-received：当前用户作为文章作者收到的申请（可按 status 过滤），PENDING 行行内「同意/拒绝」；
 * - list-mine：当前用户自己发过的申请（只读状态列表 PENDING/APPROVED/REJECTED + advice），APPROVED 行点跳文章贡献章节。
 * - {applicantId}/accept|reject：作者处理申请，service 内校验操作人是该文章作者防越权。
 * <p>
 * 同意/拒绝放本独立页，不调 confirmNoticeApi（NotifySupport needConfirm 仍 0，通知仅提醒+跳转 /collaboration）。
 * 申请提交入口在 ArticleAuthoringController.applyContributor（POST /authoring/article/{articleId}/apply-contributor）。
 */
@Tag(name = "文章贡献者", description = "前台文章贡献申请：我收到的/我申请过的 + 同意/拒绝")
@RestController
@RequestMapping("/authoring/contributor")
public class ArticleContributorController {

    @Autowired
    private ArticleContributorService articleContributorService;

    @GetMapping("/list-received")
    @Operation(summary = "我收到的文章贡献申请（我是文章作者），可按 status 过滤")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ArticleContributorVo>> listReceived(
            @RequestParam(required = false) String status) {
        PageInfo<ArticleContributorVo> page = articleContributorService.listReceived(status);
        return Result.success(page);
    }

    @GetMapping("/list-mine")
    @Operation(summary = "我申请过的文章贡献资格，可按 status 过滤")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ArticleContributorVo>> listMine(
            @RequestParam(required = false) String status) {
        PageInfo<ArticleContributorVo> page = articleContributorService.listMine(status);
        return Result.success(page);
    }

    @GetMapping("/{articleId}/my-status")
    @Operation(summary = "当前用户对该文章的贡献申请态（detail.vue 申请按钮态判定：null 未申请/PENDING/APPROVED/REJECTED）")
    @PreAuthorize("isAuthenticated()")
    public Result<String> myStatus(@PathVariable Long articleId) {
        // 作者返 null（自己文章无需申请），借此可以作为"已是作者"前端判定（detail.vue 隐藏申请按钮）
        String status = articleContributorService.myStatus(articleId);
        return Result.success(status);
    }

    @PutMapping("/{applicantId}/accept")
    @Operation(summary = "作者同意贡献申请（service 校验操作人是该文章作者）")
    @Log(title = "文章贡献审批", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> accept(@PathVariable Long applicantId) {
        Boolean b = articleContributorService.accept(applicantId);
        return Result.success(b);
    }

    @PutMapping("/{applicantId}/reject")
    @Operation(summary = "作者驳回贡献申请（advice 必填，service 校验操作人是该文章作者）")
    @Log(title = "文章贡献审批", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> reject(@PathVariable Long applicantId,
                                    @RequestParam String advice) {
        Boolean b = articleContributorService.reject(applicantId, advice);
        return Result.success(b);
    }
}