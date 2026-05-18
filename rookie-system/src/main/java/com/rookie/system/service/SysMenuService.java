package com.rookie.system.service;

import com.rookie.common.pojo.Result;
import com.rookie.common.pojo.entity.SysRole;
import com.rookie.system.pojo.quarry.MenuQuarry;
import com.rookie.system.pojo.vo.SysMenuVo;

import java.util.List;

public interface SysMenuService {

    List<SysMenuVo> quarrySysMenu(MenuQuarry menuQuarry);

    Boolean addSysMenu(SysMenuVo sysMenuVo);

    Boolean editSysMenu(SysMenuVo sysMenuVo);

    SysMenuVo getSysMenuInfo(Integer menuId);

    Boolean deleteSysMenuInfo(Integer[] menuId);

    Boolean changeSysMenuStatus(Integer menuId, Integer status);

    List<SysMenuVo> getSysMenuByRoleList(List<SysRole> roles);

}
