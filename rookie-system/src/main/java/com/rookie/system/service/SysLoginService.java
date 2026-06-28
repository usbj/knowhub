package com.rookie.system.service;

import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.pojo.vo.SysUserVo;

import java.util.List;


public interface SysLoginService {

    String loginVerification(LoginBody loginBody);

    Boolean modifyPersonalDetails(SysUserVo sysUserVo);

    SysUserVo getPersonalDetails(Long userId);

    Boolean resetSysUserPassword(Long userId,String password);

    List<SysMenuVo> getUserMenuTreeByUserId(Long userId);
}
