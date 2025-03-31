package com.rookie.system.controller;


import com.github.pagehelper.PageInfo;
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
        return sysRoleService.quarrySysRole(roleQuarry);
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "获取角色详细信息")
    public Result<SysRoleVo> getSysRoleInfo(@PathVariable Long roleId) {
        return sysRoleService.getSysRoleInfo(roleId);
    }

    @PutMapping()
    @Operation(summary = "更改角色信息")
    public Result<Boolean> editSysRoleInfo(@RequestBody SysRoleVo sysRoleVo){
        return sysRoleService.editSysRoleInfo(sysRoleVo);
    }


    @PostMapping()
    @Operation(summary = "添加角色")
    public Result<Boolean> addSysRoleInfo(@RequestBody SysRoleVo sysRoleVo){
        return sysRoleService.addSysRoleInfo(sysRoleVo);
    }

    @DeleteMapping("/{roleIds}")
    @Operation(summary = "批量删除角色")
    public Result<Boolean> deleteSysRoleInfo(@PathVariable Long[] roleIds) {
        return sysRoleService.deleteSysRoleInfo(roleIds);
    }

    @PutMapping("/status")
    @Operation(summary = "更改角色状态")
    public Result<Boolean> changeSysRoleStatus(Long roleId,Integer status){
        return sysRoleService.changeSysRoleStatus(roleId,status);
    }

    @PutMapping("/default/{roleId}")
    @Operation(summary = "设置默认角色")
    public Result<Boolean> setTheDefaultRole(@PathVariable Long roleId){
        return sysRoleService.setTheDefaultRole(roleId);
    }
}
