package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.common.vo.NoticePortalVo;
import com.knowhub.service.common.impl.NoticePortalService;
import com.rookie.common.pojo.Result;
import com.rookie.framework.security.pojo.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开公告门户接口（/portal/notice/*，全部无 @PreAuthorize，走 /portal/** permitAll）。
 * <p>
 * 铁律：仅下发群发（publish_scope='ALL'）+ 已发布（status='PUBLISHED'）+ 未删（delete=0）的公告——
 * 分组/指定成员私发公告对未登录访客不可见。公开详情对非 ALL 范围公告返 404，防私发公告被穿透。
 * <p>
 * 确认态：登录用户回填 hasConfirmed，未登录恒 false；"确认"接口仅在登录时露给前端，service 内
 * 校验 needConfirm=1 后 upsert sys_notice_read（与后台 /sys/notice/confirm 同表同语义）。
 */
@Tag(name = "公告门户", description = "前台公开公告列表/详情（未登录访客可访问）")
@RestController
@RequestMapping("/portal/notice")
public class NoticePortalController {

    @Autowired
    NoticePortalService noticePortalService;

    @GetMapping("/list")
    @Operation(summary = "前台公开公告列表（群发+已发布，置顶优先+时间倒序，分页）")
    public Result<PageInfo<NoticePortalVo>> list(
            @RequestParam(required = false) String noticeType) {
        // pageNum/pageSize 由 PageUtil.startPage 从请求参数读取
        return Result.success(noticePortalService.listPublicNotices(noticeType));
    }

    @GetMapping("/{noticeId}")
    @Operation(summary = "前台公开公告详情（非群发公告返 404）")
    public Result<NoticePortalVo> getDetail(@PathVariable Long noticeId) {
        NoticePortalVo vo = noticePortalService.getPublicNoticeById(noticeId);
        if (vo == null) {
            // 非群发公告或不存在：前台 404 语义（与资源门户详情同口径）
            return Result.error(404, "公告不存在或不可见");
        }
        return Result.success(vo);
    }

    @PostMapping("/confirm/{noticeId}")
    @Operation(summary = "确认公告（登录用户，needConfirm=1 的群发已发布公告）")
    public Result<NoticePortalVo> confirmNotice(@PathVariable Long noticeId) {
        // permitAll 区：未登录拿不到 UserInfo，传 null 给 service 统一抛"请登录后再确认"
        Long userId = currentUserOrNull();
        NoticePortalVo vo = noticePortalService.confirmNotice(noticeId, userId);
        return Result.success(vo);
    }

    /**
     * 前台 permitAll 区安全取登录态 userId：auth 为空/未认证/principal 非 UserInfo 均返 null，
     * 与 BlogPortalServiceImpl.currentUserOrNull 同范式。
     */
    private Long currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object p = auth.getPrincipal();
        return (p instanceof UserInfo userInfo) ? userInfo.getUserId() : null;
    }
}