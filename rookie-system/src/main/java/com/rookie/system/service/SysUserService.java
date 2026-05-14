package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.UserQuarry;
import com.rookie.system.pojo.vo.SysUserVo;

public interface SysUserService {

    /**
     * 获取用户列表
     * */
    PageInfo<SysUserVo> quarrySysUser(UserQuarry userQuarry);

    /**
     * 获取某一用户的详细信息
     * */
    SysUserVo selectSysUserVoById (Long userId);

    /**
     * 更改用户信息
     * */
    Boolean editSysUserInfo(SysUserVo userVo);

    /**
     * 添加用户
     * */
    Boolean addSysUserInfo(SysUserVo sysUserVo);

    /**
     * 批量删除用户
     * */
    Boolean deleteSysUser(Long[] userId);

    Boolean chargeSysUserStatus(Long userId,Integer status);

    Boolean modifyPersonalDetails(SysUserVo sysUserVo);

    SysUserVo getPersonalDetails(Long userId);

    Boolean resetSysUserPassword(Long userId,String password);

}
