package com.rookie.system.controller;


import com.github.pagehelper.PageInfo;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.RoleQuarry;
import com.rookie.system.pojo.vo.SysRoleVo;
import com.rookie.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * TODO 接口：导入、导出
 * */


@Tag(name = "角色管理", description = "用于测试角色管理的相关接口")
@RestController
@RequestMapping("/sys/role")
public class SysRoleController {

    @Autowired
    SysRoleService sysRoleService;


    @GetMapping("/list")
    @Operation(summary = "获取角色列表")
    public Result<PageInfo<SysRoleVo>> quarrySysRoleVo(RoleQuarry roleQuarry){
        PageInfo<SysRoleVo> roleVoPageInfo = sysRoleService.quarrySysRole(roleQuarry);
        return Result.success(roleVoPageInfo);
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "获取角色详细信息")
    public Result<SysRoleVo> getSysRoleInfo(@PathVariable Long roleId) {
        SysRoleVo sysRoleInfo = sysRoleService.getSysRoleInfo(roleId);
        return Result.success(sysRoleInfo);
    }

    @PutMapping()
    @Operation(summary = "更改角色信息")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    public Result<Boolean> editSysRoleInfo(@RequestBody SysRoleVo sysRoleVo){
        Boolean b = sysRoleService.editSysRoleInfo(sysRoleVo);
        return Result.success(b);
    }


    @PostMapping()
    @Operation(summary = "添加角色")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    public Result<Boolean> addSysRoleInfo(@RequestBody SysRoleVo sysRoleVo){
        Boolean b = sysRoleService.addSysRoleInfo(sysRoleVo);
        return Result.success(b);
    }

    @DeleteMapping("/{roleIds}")
    @Operation(summary = "批量删除角色")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    public Result<Boolean> deleteSysRoleInfo(@PathVariable Long[] roleIds) {
        Boolean b = sysRoleService.deleteSysRoleInfo(roleIds);
        return Result.success(b);
    }

    @PutMapping("/status")
    @Operation(summary = "更改角色状态")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    public Result<Boolean> changeSysRoleStatus(Long roleId,Integer status){
        Boolean b = sysRoleService.changeSysRoleStatus(roleId, status);
        return Result.success(b);
    }

    @PutMapping("/default/{roleId}")
    @Operation(summary = "设置默认角色")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    public Result<Boolean> setTheDefaultRole(@PathVariable Long roleId){
        Boolean b = sysRoleService.setTheDefaultRole(roleId);
        return Result.success(b);
    }
}
