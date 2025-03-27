package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.UserQuarry;
import com.rookie.system.pojo.vo.SysUserVo;

public interface SysUserService {

    /**
     * 获取用户列表
     * */
    Result<PageInfo<SysUserVo>> quarrySysUser(UserQuarry userQuarry);

    /**
     * 获取某一用户的详细信息
     * */
    Result<SysUserVo> selectSysUserVoById (Long userId);

    /**
     * 更改用户信息
     * */
    Result<Boolean> editSysUserInfo(SysUserVo userVo);

    /**
     * 添加用户
     * */
    Result<Boolean> addSysUserInfo(SysUserVo sysUserVo);

    /**
     * 批量删除用户
     * */
    Result<Boolean> deleteSysUser(Long[] userId);

    Result<Boolean> chargeSysUserStatus(Long userId,Integer status);

    Result<Boolean> modifyPersonalDetails(SysUserVo sysUserVo);

    Result<SysUserVo> getPersonalDetails(Long userId);

    Result<Boolean> resetSysUserPassword(Long userId,String password);

}
