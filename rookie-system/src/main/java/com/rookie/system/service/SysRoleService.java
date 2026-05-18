package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.entity.SysRole;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.RoleQuarry;
import com.rookie.system.pojo.vo.SysRoleVo;

import java.util.List;

public interface SysRoleService {

    PageInfo<SysRoleVo> quarrySysRole(RoleQuarry roleQuarry);

    /**
     * 用于获取角色信息，以用于前端对信息修改
     * */
    SysRoleVo getSysRoleInfo(Long roleId);

    Boolean editSysRoleInfo(SysRoleVo sysRoleVo);

    Boolean addSysRoleInfo(SysRoleVo sysRoleVo);

    Boolean deleteSysRoleInfo(Long[] roleId);

    Boolean changeSysRoleStatus(Long roleId,Integer status);

    Boolean setTheDefaultRole(Long roleId);

    List<SysRole> getSysRoleByUserId(Long userId);

}
