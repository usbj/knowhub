package com.rookie.system.controller;

import com.github.pagehelper.PageInfo;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.pojo.quarry.NoticeQuarry;
import com.rookie.system.pojo.vo.SysNoticeVo;
import com.rookie.system.service.SysNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "消息通知", description = "消息通知管理相关接口")
@RestController
@RequestMapping("/sys/notice")
public class SysNoticeController {

    @Autowired
    SysNoticeService sysNoticeService;

    @GetMapping("/list")
    @Operation(summary = "获取消息通知列表")
    public Result<PageInfo<SysNoticeVo>> quarrySysNotice(NoticeQuarry quarry) {
        PageInfo<SysNoticeVo> pageInfo = sysNoticeService.quarrySysNotice(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{noticeId}")
    @Operation(summary = "获取消息通知详情")
    public Result<SysNoticeVo> getSysNoticeInfo(@PathVariable Long noticeId) {
        SysNoticeVo vo = sysNoticeService.getSysNoticeInfo(noticeId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "添加消息通知")
    @Log(title = "消息通知", businessType = BusinessType.INSERT)
    public Result<Boolean> addSysNotice(@RequestBody SysNoticeVo vo) {
        Boolean b = sysNoticeService.addSysNoticeInfo(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑消息通知")
    @Log(title = "消息通知", businessType = BusinessType.UPDATE)
    public Result<Boolean> editSysNotice(@RequestBody SysNoticeVo vo) {
        Boolean b = sysNoticeService.editSysNoticeInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{noticeIds}")
    @Operation(summary = "批量删除消息通知")
    @Log(title = "消息通知", businessType = BusinessType.DELETE)
    public Result<Boolean> deleteSysNotice(@PathVariable Long[] noticeIds) {
        Boolean b = sysNoticeService.deleteSysNoticeInfo(noticeIds);
        return Result.success(b);
    }

    @PutMapping("/publish/{noticeId}")
    @Operation(summary = "发布消息通知")
    @Log(title = "消息通知", businessType = BusinessType.UPDATE)
    public Result<Boolean> publishSysNotice(@PathVariable Long noticeId) {
        Boolean b = sysNoticeService.publishSysNotice(noticeId);
        return Result.success(b);
    }

    @PutMapping("/revoke/{noticeId}")
    @Operation(summary = "撤回消息通知")
    @Log(title = "消息通知", businessType = BusinessType.UPDATE)
    public Result<Boolean> revokeSysNotice(@PathVariable Long noticeId) {
        Boolean b = sysNoticeService.revokeSysNotice(noticeId);
        return Result.success(b);
    }

    @GetMapping("/my")
    @Operation(summary = "获取当前用户的消息列表")
    public Result<List<SysNoticeVo>> getMyNotices() {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<SysNoticeVo> list = sysNoticeService.getMyNotices(userInfo.getUserId());
        return Result.success(list);
    }

    @PostMapping("/read/{noticeId}")
    @Operation(summary = "标记消息为已读")
    public Result<Boolean> markAsRead(@PathVariable Long noticeId) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Boolean b = sysNoticeService.markAsRead(noticeId, userInfo.getUserId());
        return Result.success(b);
    }

    @PostMapping("/confirm/{noticeId}")
    @Operation(summary = "确认消息通知")
    public Result<Boolean> confirmNotice(@PathVariable Long noticeId) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Boolean b = sysNoticeService.confirmNotice(noticeId, userInfo.getUserId());
        return Result.success(b);
    }
}
