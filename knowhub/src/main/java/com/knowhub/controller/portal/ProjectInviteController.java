package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.project.vo.ProjectInviteVo;
import com.knowhub.service.project.impl.ProjectInviteService;
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
 * 前台项目邀请处理接口（/authoring/invite/**，走 /authoring/** authenticated 兜底，无按钮权限键）。
 * <p>
 * 前台「我的协作」页「我收到的项目邀请」tab 的后端。
 * - list-received：当前登录用户收到的邀请（可按 status 过滤），PENDING 行行内「同意/拒绝」；
 * - {inviteId}/accept|reject：受邀人处理邀请，service 内校验操作人是受邀人本人防越权。
 * 发起邀请入口在 ProjectAuthoringController.inviteMember（POST /authoring/project/{projectId}/invite）。
 * <p>
 * 同意/拒绝放本独立页，不调 confirmNoticeApi（NotifySupport needConfirm 仍 0，通知仅提醒+跳转 /collaboration）。
 */
@Tag(name = "项目邀请", description = "前台项目成员邀请：我收到的 + 同意/拒绝")
@RestController
@RequestMapping("/authoring/invite")
public class ProjectInviteController {

    @Autowired
    private ProjectInviteService projectInviteService;

    @GetMapping("/list-received")
    @Operation(summary = "我收到的项目邀请，可按 status 过滤")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ProjectInviteVo>> listReceived(
            @RequestParam(required = false) String status) {
        PageInfo<ProjectInviteVo> page = projectInviteService.listReceived(status);
        return Result.success(page);
    }

    @PutMapping("/{inviteId}/accept")
    @Operation(summary = "受邀人同意邀请（写成员 MEMBER 行 + 邀请置 ACCEPTED + 通知负责人）")
    @Log(title = "项目邀请处理", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> accept(@PathVariable Long inviteId) {
        Boolean b = projectInviteService.accept(inviteId);
        return Result.success(b);
    }

    @PutMapping("/{inviteId}/reject")
    @Operation(summary = "受邀人拒绝邀请（仅置 REJECTED + 通知负责人，不写成员表）")
    @Log(title = "项目邀请处理", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> reject(@PathVariable Long inviteId) {
        Boolean b = projectInviteService.reject(inviteId);
        return Result.success(b);
    }
}