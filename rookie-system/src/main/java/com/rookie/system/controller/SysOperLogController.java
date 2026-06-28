package com.rookie.system.controller;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.OperLogQuarry;
import com.rookie.system.pojo.vo.SysOperLogVo;
import com.rookie.system.service.SysOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "操作日志", description = "操作日志管理相关接口")
@RestController
@RequestMapping("/sys/operLog")
public class SysOperLogController {

    @Autowired
    private SysOperLogService sysOperLogService;

    @GetMapping("/list")
    @Operation(summary = "获取操作日志列表")
    @PreAuthorize("hasAuthority('system:operLog:quarry')")
    public Result<PageInfo<SysOperLogVo>> quarryOperLog(OperLogQuarry quarry) {
        PageInfo<SysOperLogVo> pageInfo = sysOperLogService.quarryOperLog(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{operId}")
    @Operation(summary = "获取操作日志详情")
    @PreAuthorize("hasAuthority('system:operLog:info')")
    public Result<SysOperLogVo> getOperLogInfo(@PathVariable Long operId) {
        SysOperLogVo vo = sysOperLogService.getOperLogInfo(operId);
        return Result.success(vo);
    }

    @DeleteMapping("/{operIds}")
    @Operation(summary = "批量删除操作日志")
    @PreAuthorize("hasAuthority('system:operLog:delete')")
    public Result<Boolean> deleteOperLog(@PathVariable Long[] operIds) {
        Boolean b = sysOperLogService.deleteOperLog(operIds);
        return Result.success(b);
    }

    @DeleteMapping("/clean")
    @Operation(summary = "清空操作日志")
    @PreAuthorize("hasAuthority('system:operLog:clean')")
    public Result<Boolean> cleanOperLog() {
        Boolean b = sysOperLogService.cleanOperLog();
        return Result.success(b);
    }
}
