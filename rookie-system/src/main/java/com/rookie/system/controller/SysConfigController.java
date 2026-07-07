package com.rookie.system.controller;

import com.github.pagehelper.PageInfo;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.SysConfigQuarry;
import com.rookie.system.pojo.vo.SysConfigVo;
import com.rookie.system.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统设置 Controller。
 * <p>
 * 提供系统设置的分页查询、详情、新增、编辑、删除、刷新缓存六个接口。
 * 写操作加 {@link Log} 采集操作日志，全部接口加 {@code @PreAuthorize} 鉴权，
 * 权限码与 {@code sys_menu.perm_key} 对齐：{@code system:systemConfig:*}。
 */
@RestController
@RequestMapping("/sys/system-config")
public class SysConfigController {

    @Autowired
    SysConfigService sysConfigService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:systemConfig:quarry')")
    public Result<PageInfo<SysConfigVo>> quarrySysConfig(SysConfigQuarry quarry) {
        PageInfo<SysConfigVo> pageInfo = sysConfigService.quarrySysConfig(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{configId}")
    @PreAuthorize("hasAuthority('system:systemConfig:info')")
    public Result<SysConfigVo> getSysConfigInfo(@PathVariable Long configId) {
        SysConfigVo configVo = sysConfigService.getSysConfigById(configId);
        return Result.success(configVo);
    }

    @PostMapping()
    @Log(title = "系统设置", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('system:systemConfig:add')")
    public Result<Boolean> addSysConfig(@RequestBody SysConfigVo configVo) {
        Boolean ok = sysConfigService.addSysConfig(configVo);
        return Result.success(ok);
    }

    @PutMapping()
    @Log(title = "系统设置", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('system:systemConfig:edit')")
    public Result<Boolean> editSysConfig(@RequestBody SysConfigVo configVo) {
        Boolean ok = sysConfigService.editSysConfig(configVo);
        return Result.success(ok);
    }

    @DeleteMapping("/{configId}")
    @Log(title = "系统设置", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('system:systemConfig:delete')")
    public Result<Boolean> deleteSysConfigById(@PathVariable Long configId) {
        Boolean ok = sysConfigService.deleteSysConfigById(configId);
        return Result.success(ok);
    }

    @PostMapping("/refresh")
    @Log(title = "系统设置", businessType = BusinessType.OTHER)
    @PreAuthorize("hasAuthority('system:systemConfig:refresh')")
    public Result<Boolean> refreshCache() {
        Boolean ok = sysConfigService.refreshCache();
        return Result.success(ok);
    }

    /**
     * 全量查询启用系统设置项，供前端启动加载消费。
     * <p>
     * 公共读取接口：不分页、不加按钮权限、不记操作日志，仅需登录即可调用，
     * 对标字典 {@code GET /sys/dict/data/type/{dictKey}}。
     */
    @GetMapping("/list-all")
    public Result<List<SysConfigVo>> listAllEnabledSysConfig() {
        List<SysConfigVo> list = sysConfigService.listAllEnabledSysConfig();
        return Result.success(list);
    }
}
