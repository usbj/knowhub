package com.rookie.system.controller;


import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.Result;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.pojo.quarry.UserQuarry;
import com.rookie.system.pojo.vo.SysUserVo;
import com.rookie.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * TODO 缺批量上传用户、导出数据、头像上传
 * */

@RestController
@RequestMapping("/sys/user")
@Tag(name = "用户管理",description = "用于测试用户相关接口")
public class SysUserController  {

    @Autowired
    SysUserService sysUserService;

    @GetMapping("/list")
    @Operation(summary = "获取用户列表")
    public Result<PageInfo<SysUserVo>> quarrySysUserList(UserQuarry userQuarry) {
        return sysUserService.quarrySysUser(userQuarry);
    }

    @PutMapping()
    @Operation(summary = "更改用户信息")
    public Result<Boolean> editSysUserInfo(@RequestBody SysUserVo userVo) {
        return sysUserService.editSysUserInfo(userVo);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "获取用户详细信息")
    public Result<SysUserVo> getSysUserInfo(@PathVariable Long userId ) {
        return sysUserService.selectSysUserVoById(userId);
    }

    @PostMapping()
    @Operation(summary = "添加用户")
    public Result<Boolean> addSysUser(@RequestBody SysUserVo userVo) {
        return sysUserService.addSysUserInfo(userVo);
    }

    @DeleteMapping("/{userIds}")
    @Operation(summary = "删除用户（可批量）")
    public Result<Boolean> deleteSysUsers(@PathVariable Long[] userIds) {
        return sysUserService.deleteSysUser(userIds);
    }

    @PutMapping("/status")
    @Operation(summary = "更改用户状态")
    public Result<Boolean> changeSysUserStatus(Long userId, Integer status) {
        return sysUserService.chargeSysUserStatus(userId,status);
    }

    @GetMapping("/person")
    @Operation(summary = "获取个人数据")
    public Result<SysUserVo> getPersonalDetail() {
        UserInfo userInfo = (UserInfo)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return sysUserService.getPersonalDetails(userInfo.getUserId());
    }

    @PutMapping("/person")
    @Operation(summary = "更改个人数据")
    public Result<Boolean> modifyPersonalDetails(@RequestBody SysUserVo sysUserVo) {
        return sysUserService.modifyPersonalDetails(sysUserVo);
    }


}
