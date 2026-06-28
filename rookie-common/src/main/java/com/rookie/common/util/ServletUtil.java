package com.rookie.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


public class ServletUtil {


    //非controller获取request属性
    public static ServletRequestAttributes getRequestAttributes() {
        return (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
    }

    //获取request属性里面的request请求
    public static HttpServletRequest getRequest() {
        return getRequestAttributes().getRequest();
    }

    //获取请求参数
    public static String getParameter(String key) {
        return getRequest().getParameter(key);
    }

    public static String getRequestCompleteURL(){
        return "["+getRequest().getMethod()+"] "+getRequest().getRequestURI();
    }

}
