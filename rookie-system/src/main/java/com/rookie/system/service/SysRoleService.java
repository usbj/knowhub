package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.RoleQuarry;
import com.rookie.system.pojo.vo.SysRoleVo;

public interface SysRoleService {

    Result<PageInfo<SysRoleVo>> quarrySysRole(RoleQuarry roleQuarry);

    /**
     * 用于获取角色信息，以用于前端对信息修改
     * */
    Result<SysRoleVo> getSysRoleInfo(Long roleId);

    Result<Boolean> editSysRoleInfo(SysRoleVo sysRoleVo);

    Result<Boolean> addSysRoleInfo(SysRoleVo sysRoleVo);

    Result<Boolean> deleteSysRoleInfo(Long[] roleId);

    Result<Boolean> changeSysRoleStatus(Long roleId,Integer status);

    Result<Boolean> setTheDefaultRole(Long roleId);

}
