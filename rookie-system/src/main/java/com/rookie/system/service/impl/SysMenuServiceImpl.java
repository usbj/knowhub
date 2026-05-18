package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import com.rookie.common.pojo.entity.SysMenu;
import com.rookie.common.pojo.entity.SysRole;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysMenuMapper;
import com.rookie.system.mapper.SysRoleMenuMapper;
import com.rookie.system.pojo.SysRoleMenu;
import com.rookie.system.pojo.quarry.MenuQuarry;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.service.SysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;



@Service
public class SysMenuServiceImpl implements SysMenuService {

    @Autowired
    SysMenuMapper sysMenuMapper;

    @Autowired
    SysRoleMenuMapper sysRoleMenuMapper;


    @Override
    public List<SysMenuVo> quarrySysMenu(MenuQuarry menuQuarry) {
        //获取数据
        List<SysMenuVo> sysMenuVos = sysMenuMapper.quarrySysMenu(menuQuarry);
        return buildMenuTree(sysMenuVos);
    }

    @Override
    public Boolean addSysMenu(SysMenuVo sysMenuVo) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysMenu sysMenu = BeanUtil.toBean(sysMenuVo, SysMenu.class);
        sysMenu.setCreateBy(userInfo.getUsername());
        sysMenu.setUpdateBy(userInfo.getUsername());
        try {
            sysMenuMapper.addSysMenu(sysMenu);
        } catch (Exception e) {
            throw new ServiceException(500,"菜单添加失败");
        }

        return true;
    }

    @Override
    public Boolean editSysMenu(SysMenuVo sysMenuVo) {
        SysMenu sysMenu = BeanUtil.toBean(sysMenuVo, SysMenu.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysMenu.setUpdateBy(userInfo.getUsername());
        try {
            sysMenuMapper.editSysMenuInfo(sysMenu);
        } catch (Exception e) {
            throw new ServiceException(500,"菜单更改失败");
        }
        return true;
    }

    @Override
    public SysMenuVo getSysMenuInfo(Integer menuId) {
        SysMenu sysMenuInfo = sysMenuMapper.getSysMenuInfo(menuId);
        return BeanUtil.toBean(sysMenuInfo, SysMenuVo.class);
    }

    @Override
    public Boolean deleteSysMenuInfo(Integer[] menuIds) {
        try {
            for (Integer i : menuIds){
                sysMenuMapper.deleteSysMenuInfo(i);
            }
        } catch (Exception e) {
            throw new ServiceException(500,"菜单删除失败");
        }
        return true;
    }

    @Override
    public Boolean changeSysMenuStatus(Integer menuId, Integer status) {
        try {
            sysMenuMapper.changeSysMenuStatus(menuId, status);
        } catch (Exception e) {
            throw new ServiceException(500,"菜单状态更改失败");
        }
        return true;
    }

    @Override
    public List<SysMenuVo> getSysMenuByRoleList(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> roleIds = roles.stream().map(SysRole::getRoleId).distinct().toList();
        List<SysRoleMenu> roleMenus = sysRoleMenuMapper.getRoleMenuByRoleIds(roleIds);
        if (roleMenus == null || roleMenus.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).distinct().toList();
        return sysMenuMapper.getSysMenuByMenuIds(menuIds);
    }

    private List<SysMenuVo> buildMenuTree(List<SysMenuVo> sysMenuVos) {
        HashMap<Long,SysMenuVo> hashMap = new HashMap<>();
        for (SysMenuVo sysMenuVo : sysMenuVos) {
            if(sysMenuVo.getSonMenus()==null){
                sysMenuVo.setSonMenus(new ArrayList<>());
            }
            hashMap.put(sysMenuVo.getMenuId(),sysMenuVo);
        }
        List<SysMenuVo> menuVos = new ArrayList<>();
        for (SysMenuVo sysMenuVo : sysMenuVos) {
            Long id = sysMenuVo.getParentId();
            if (id == null || id == -1L){
                menuVos.add(sysMenuVo);
                continue;
            }
            SysMenuVo parentMenu = hashMap.get(sysMenuVo.getParentId());
            if (parentMenu != null) {
                parentMenu.getSonMenus().add(sysMenuVo);
            }
        }

        return menuVos;
    }
}
