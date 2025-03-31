package com.rookie.system.mapper;

import com.rookie.system.pojo.SysRoleMenu;

import java.util.List;

public interface SysRoleMenuMapper {

    Boolean deleteSyeRoleMenu(Long roleId);

    Boolean insertSysRoleMenu(List<SysRoleMenu> sysRoleMenus);

}
