package com.rookie.system.controller;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.ErrorLogQuarry;
import com.rookie.system.pojo.vo.SysErrorLogVo;
import com.rookie.system.service.SysErrorLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "错误日志", description = "错误日志管理相关接口")
@RestController
@RequestMapping("/sys/errorLog")
public class SysErrorLogController {

    @Autowired
    private SysErrorLogService sysErrorLogService;

    @GetMapping("/list")
    @Operation(summary = "获取错误日志列表")
    @PreAuthorize("hasAuthority('system:errorLog:quarry')")
    public Result<PageInfo<SysErrorLogVo>> quarryErrorLog(ErrorLogQuarry quarry) {
        PageInfo<SysErrorLogVo> pageInfo = sysErrorLogService.quarryErrorLog(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{errorId}")
    @Operation(summary = "获取错误日志详情")
    @PreAuthorize("hasAuthority('system:errorLog:info')")
    public Result<SysErrorLogVo> getErrorLogInfo(@PathVariable Long errorId) {
        SysErrorLogVo vo = sysErrorLogService.getErrorLogInfo(errorId);
        return Result.success(vo);
    }

    @DeleteMapping("/{errorIds}")
    @Operation(summary = "批量删除错误日志")
    @PreAuthorize("hasAuthority('system:errorLog:delete')")
    public Result<Boolean> deleteErrorLog(@PathVariable Long[] errorIds) {
        Boolean b = sysErrorLogService.deleteErrorLog(errorIds);
        return Result.success(b);
    }

    @DeleteMapping("/clean")
    @Operation(summary = "清空错误日志")
    @PreAuthorize("hasAuthority('system:errorLog:clean')")
    public Result<Boolean> cleanErrorLog() {
        Boolean b = sysErrorLogService.cleanErrorLog();
        return Result.success(b);
    }
}
