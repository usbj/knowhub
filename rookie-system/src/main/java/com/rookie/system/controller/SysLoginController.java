package com.rookie.system.controller;


import com.rookie.common.pojo.Result;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.pojo.vo.SysUserVo;
import com.rookie.system.service.SysLoginService;
import com.rookie.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TODO:重构登录系统，理清UserInfo,SysUserVO的区分，确定是用SysLoginService还是SysUserService抑或其他
 * */

@RestController
@Tag(name = "系统登录",description = "用于测试登录相关接口")
public class SysLoginController {

    @Autowired
    SysLoginService sysLoginService;


    @PostMapping("/login")
    @Operation(summary = "登录")
    public Result<String> login(@RequestBody LoginBody loginBody){
        String token = sysLoginService.loginVerification(loginBody);
        return Result.success(token);
    }


    @GetMapping("/person")
    @Operation(summary = "获取个人数据")
    public Result<SysUserVo> getPersonalDetail() {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysUserVo personalDetails = sysLoginService.getPersonalDetails(userInfo.getUserId());
        return Result.success(personalDetails);
    }

    @PutMapping("/person")
    @Operation(summary = "更改个人数据")
    public Result<Boolean> modifyPersonalDetails(@RequestBody SysUserVo sysUserVo) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysUserVo.setUserId(userInfo.getUserId());
        Boolean b = sysLoginService.modifyPersonalDetails(sysUserVo);
        return Result.success(b);
    }

    @GetMapping("/person/routers")
    @Operation(summary = "获取当前用户路由树")
    public Result<List<SysMenuVo>> getUserMenuTree() {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<SysMenuVo> userMenuTreeByUserId = sysLoginService.getUserMenuTreeByUserId(userInfo.getUserId());
        return Result.success(userMenuTreeByUserId);
    }

}
