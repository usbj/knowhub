package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysMenu;
import com.rookie.system.pojo.quarry.MenuQuarry;
import com.rookie.system.pojo.vo.SysMenuVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface SysMenuMapper {

    List<SysMenuVo> quarrySysMenu(MenuQuarry menuQuarry);

    boolean addSysMenu(SysMenu sysMenu);

    boolean editSysMenuInfo(SysMenu sysMenu);

    SysMenu getSysMenuInfo(Integer menuId);

    boolean deleteSysMenuInfo(Integer menuId);

    boolean changeSysMenuStatus(Integer menuId,Integer status);

}
