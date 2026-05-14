package com.rookie.system.controller;


import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.MenuQuarry;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sys/menu")
@Tag(name = "菜单管理")
public class SysMenuController {

    @Autowired
    SysMenuService sysMenuService;


    @GetMapping("/list")
    @Operation(summary = "菜单列表")
    public Result<List<SysMenuVo>> quarrySysMenu(MenuQuarry menuQuarry) {
        List<SysMenuVo> sysMenuVos = sysMenuService.quarrySysMenu(menuQuarry);
        return Result.success(sysMenuVos);
    }

    @PostMapping()
    @Operation(summary = "添加菜单")
    public Result<Boolean> addSysMenu(@RequestBody SysMenuVo menuVo) {
        Boolean b = sysMenuService.addSysMenu(menuVo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "更改菜单信息")
    public Result<Boolean> editSysMenu(@RequestBody SysMenuVo menuVo){
        Boolean b = sysMenuService.editSysMenu(menuVo);
        return Result.success(b);
    }

    @GetMapping("/{menuId}")
    @Operation(summary = "获取菜单详情")
    public Result<SysMenuVo> sysMenuInfo(@PathVariable Integer menuId){
        SysMenuVo sysMenuInfo = sysMenuService.getSysMenuInfo(menuId);
        return Result.success(sysMenuInfo);
    }

    @DeleteMapping("/{menuIds}")
    @Operation(summary = "删除菜单信息")
    public Result<Boolean> deleteSysMenu(@PathVariable Integer[] menuIds){
        Boolean b = sysMenuService.deleteSysMenuInfo(menuIds);
        return Result.success(b);
    }

    @PutMapping("/status")
    @Operation(summary = "更改菜单状态")
    public Result<Boolean> changeSysMenuStatus(Integer menuId,Integer status){
        Boolean b = sysMenuService.changeSysMenuStatus(menuId, status);
        return Result.success(b);
    }

}
