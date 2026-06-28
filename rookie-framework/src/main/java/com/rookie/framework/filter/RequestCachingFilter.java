package com.rookie.framework.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 请求体缓存过滤器
 * HTTP 请求体 InputStream 只能读一次：Controller 的 @RequestBody 读完之后，
 * LogAspect 切面后置阶段无法再读请求参数。
 * 用 ContentCachingRequestWrapper 包装 request，切面阶段通过 getContentAsByteArray() 即可再次读取。
 * 必须在 TokenVerifyFilter 之前执行，故 order 设为最低优先级（最先触发）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 仅包装需要缓存请求体的请求（GET 等无请求体场景也安全：包装类按需读取，无副作用）
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        filterChain.doFilter(wrappedRequest, response);
    }
}
