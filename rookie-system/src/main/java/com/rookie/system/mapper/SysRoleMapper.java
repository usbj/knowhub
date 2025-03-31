package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysRole;
import com.rookie.system.pojo.quarry.RoleQuarry;
import com.rookie.system.pojo.vo.SysRoleVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface SysRoleMapper {

    SysRole getDefaultRole();

    List<SysRole> quarrySysRole(RoleQuarry roleQuarry);

    SysRoleVo getSysRoleInfo(Long roleId);

    Boolean editSysRoleInfo(SysRole sysRole);

    Boolean addSysRoleInfo(SysRole sysRole);

    Boolean deleteSysRoleInfo(Long roleId);

    Boolean changeSysRoleStatus(Long roleId,Integer status);

    Boolean cancelTheDefaultRole();

    Boolean setTheDefaultRole(Long roleId);

}
