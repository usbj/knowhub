package com.rookie.system.controller;

import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.RegisterBody;
import com.rookie.system.service.SysLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自助注册 Controller。
 * <p>
 * 注册接口 {@code POST /register} 为公开接口（SecurityConfig 已 permitAll），
 * 注册开关由系统设置 {@code sys.user.registerEnabled}（BOOLEAN）控制，
 * 关闭时由 {@link SysLoginService#register} 直接拒绝；注册成功后绑定系统默认角色，
 * 不自动登录，由前端引导跳转登录页。
 */
@RestController
@Tag(name = "用户注册", description = "用户自助注册相关接口")
public class SysRegisterController {

    @Autowired
    SysLoginService sysLoginService;

    @PostMapping("/register")
    @Operation(summary = "用户自助注册")
    @Log(title = "用户注册", businessType = BusinessType.INSERT)
    public Result<Boolean> register(@RequestBody RegisterBody registerBody) {
        Boolean ok = sysLoginService.register(registerBody);
        return Result.success(ok);
    }
}
