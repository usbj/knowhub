package com.rookie.system.service;

import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.MenuQuarry;
import com.rookie.system.pojo.vo.SysMenuVo;

import java.util.List;

public interface SysMenuService {

    Result<List<SysMenuVo>> quarrySysMenu(MenuQuarry menuQuarry);

    Result<Boolean> addSysMenu(SysMenuVo sysMenuVo);

    Result<Boolean> editSysMenu(SysMenuVo sysMenuVo);

    Result<SysMenuVo> getSysMenuInfo(Integer menuId);

    Result<Boolean> deleteSysMenuInfo(Integer[] menuId);

    Result<Boolean> changeSysMenuStatus(Integer menuId, Integer status);

}
