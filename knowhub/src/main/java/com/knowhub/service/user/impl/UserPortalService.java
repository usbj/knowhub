package com.knowhub.service.user.impl;

import com.knowhub.pojo.user.vo.UserPortalVo;

/**
 * 用户公开主页 Service（/portal/user/{userId}，游客可读）。
 * <p>
 * 薄封装 {@link com.knowhub.mapper.user.UserPortalMapper}，按 userId 取公开主页信息。
 * 不校验登录态（接口走 /portal/** permitAll）；返回 null 时由 Controller 转为业务错误。
 * 与 {@link com.knowhub.service.user.AuthoringUserServiceImpl} 区别：本接口按 userId 单查，
 * 非模糊搜索，且不暴露 phoneNumber（隐私）。
 *
 * @author knowhub
 */
public interface UserPortalService {

    /** 按 userId 查公开主页信息，用户不存在或已停用返回 null */
    UserPortalVo getPublicUserById(Long userId);
}
