package com.rookie.system.service;

import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.pojo.ModifyPasswordBody;
import com.rookie.system.pojo.RegisterBody;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.pojo.vo.SysUserVo;

import java.util.List;


public interface SysLoginService {

    String loginVerification(LoginBody loginBody);

    /**
     * 用户自助注册。
     * <p>
     * 受系统设置 {@code sys.user.registerEnabled}（BOOLEAN）开关控制，关闭时直接拒绝；
     * 注册成功后自动绑定系统默认角色（sys_role.is_default=1），注册即启用（status=1），
     * 不自动登录，由前端引导跳转登录页。
     *
     * @param registerBody 注册请求体（username/password 必填，nickName/phoneNumber/sex 可选）
     * @return 是否注册成功
     */
    Boolean register(RegisterBody registerBody);

    /**
     * 修改当前用户密码（独立于资料编辑）。
     * <p>
     * 校验原密码（BCrypt matches）通过后加密落库；不更新其他任何资料字段。
     *
     * @param userId 当前登录用户主键
     * @param body   修改密码请求体（oldPassword / newPassword）
     * @return 是否修改成功
     */
    Boolean modifyPersonalPassword(Long userId, ModifyPasswordBody body);

    Boolean modifyPersonalDetails(SysUserVo sysUserVo);

    SysUserVo getPersonalDetails(Long userId);

    Boolean resetSysUserPassword(Long userId,String password);

    List<SysMenuVo> getUserMenuTreeByUserId(Long userId);
}
