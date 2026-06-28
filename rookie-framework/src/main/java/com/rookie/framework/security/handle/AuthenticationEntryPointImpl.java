package com.rookie.framework.security.handle;

import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.json.JSONUtil;
import com.rookie.common.pojo.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        JakartaServletUtil.write(response,
                JSONUtil.toJsonStr(
                        Result.error(401,
                                String.format("请求访问：%s,但认证失败，无法访问系统资源",request.getRequestURI())
                        )
                )
                , MediaType.APPLICATION_JSON_UTF8_VALUE);
    }
}
