package com.rookie.system.controller;


import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.service.SysLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sys/login")
@Tag(name = "系统登录",description = "用于测试登录相关接口")
public class SysLoginController {

    @Autowired
    SysLoginService sysLoginService;

    @PostMapping
    @Operation(summary = "登录")
    public Result<String> login(@RequestBody LoginBody loginBody){
        String token = sysLoginService.loginVerification(loginBody);
        return Result.success(token);
    }

}
