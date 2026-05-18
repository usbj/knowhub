package com.rookie.system.mapper;

import com.rookie.system.pojo.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper {

    Boolean deleteSyeRoleMenu(Long roleId);

    Boolean insertSysRoleMenu(List<SysRoleMenu> sysRoleMenus);

    List<SysRoleMenu> getRoleMenuByRoleIds(List<Long> roleIds);

}
