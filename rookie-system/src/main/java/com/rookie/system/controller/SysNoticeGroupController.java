package com.rookie.system.controller;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.NoticeGroupQuarry;
import com.rookie.system.pojo.vo.SysNoticeGroupVo;
import com.rookie.system.service.SysNoticeGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "消息通知分组", description = "消息通知分组管理相关接口")
@RestController
@RequestMapping("/sys/notice/group")
public class SysNoticeGroupController {

    @Autowired
    SysNoticeGroupService sysNoticeGroupService;

    @GetMapping("/list")
    @Operation(summary = "获取通知分组列表")
    public Result<PageInfo<SysNoticeGroupVo>> quarrySysNoticeGroup(NoticeGroupQuarry quarry) {
        PageInfo<SysNoticeGroupVo> page = sysNoticeGroupService.quarrySysNoticeGroup(quarry);
        return Result.success(page);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "获取通知分组详情")
    public Result<SysNoticeGroupVo> getSysNoticeGroupInfo(@PathVariable Long groupId) {
        SysNoticeGroupVo vo = sysNoticeGroupService.getSysNoticeGroupInfo(groupId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "添加通知分组")
    public Result<Boolean> addSysNoticeGroup(@RequestBody SysNoticeGroupVo vo) {
        Boolean b = sysNoticeGroupService.addSysNoticeGroupInfo(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑通知分组")
    public Result<Boolean> editSysNoticeGroup(@RequestBody SysNoticeGroupVo vo) {
        Boolean b = sysNoticeGroupService.editSysNoticeGroupInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{groupIds}")
    @Operation(summary = "批量删除通知分组")
    public Result<Boolean> deleteSysNoticeGroup(@PathVariable Long[] groupIds) {
        Boolean b = sysNoticeGroupService.deleteSysNoticeGroupInfo(groupIds);
        return Result.success(b);
    }

    @PostMapping("/{groupId}/members")
    @Operation(summary = "向分组批量添加成员")
    public Result<Boolean> addMembers(@PathVariable Long groupId, @RequestBody List<Long> userIds) {
        Boolean b = sysNoticeGroupService.addMembers(groupId, userIds);
        return Result.success(b);
    }

    @DeleteMapping("/{groupId}/members")
    @Operation(summary = "批量移除分组成员")
    public Result<Boolean> removeMembers(@PathVariable Long groupId, @RequestBody List<Long> memberIds) {
        Boolean b = sysNoticeGroupService.removeMembers(groupId, memberIds);
        return Result.success(b);
    }
}
