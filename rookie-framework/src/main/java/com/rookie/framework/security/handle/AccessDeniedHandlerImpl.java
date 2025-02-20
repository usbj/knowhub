package com.rookie.framework.security.handle;

import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.json.JSONUtil;
import com.rookie.common.pojo.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        JakartaServletUtil.write(response,
                JSONUtil.toJsonStr(
                        Result.error(403,
                                String.format("请求访问：%s,但没有权限，无法访问系统资源",request.getRequestURI())
                        )
                )
                , MediaType.APPLICATION_JSON_UTF8_VALUE
        );
    }
}
