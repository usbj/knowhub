package com.rookie.system.service.impl;


import com.rookie.common.pojo.Result;
import com.rookie.framework.security.service.TokenService;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.service.SysLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;


@Service
public class SysLoginServiceImpl implements SysLoginService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    TokenService tokenService;



    @Override
    public Result<String> loginVerification(LoginBody loginBody) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken
                        (loginBody.getUsername(),loginBody.getPassword());
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);
        if (authenticate == null){
            return Result.error();
        }
        UserInfo userInfo = (UserInfo) authenticate.getPrincipal();

        String token = tokenService.createJwt(userInfo);
        return Result.success(token);
    }
}
