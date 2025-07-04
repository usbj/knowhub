package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import com.rookie.common.pojo.entity.SysMenu;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysMenuMapper;
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


    @Override
    public Result<List<SysMenuVo>> quarrySysMenu(MenuQuarry menuQuarry) {
        //获取数据
        List<SysMenuVo> sysMenuVos = sysMenuMapper.quarrySysMenu(menuQuarry);
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
            if (id ==-1){
                menuVos.add(sysMenuVo);
                continue;
            }
            hashMap.get(sysMenuVo.getParentId()).getSonMenus().add(sysMenuVo);
        }

        return Result.success(menuVos);
    }

    @Override
    public Result<Boolean> addSysMenu(SysMenuVo sysMenuVo) {
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysMenu sysMenu = BeanUtil.toBean(sysMenuVo, SysMenu.class);
        sysMenu.setCreateBy(userInfo.getUsername());
        sysMenu.setUpdateBy(userInfo.getUsername());
        try {
            sysMenuMapper.addSysMenu(sysMenu);
        } catch (Exception e) {
            throw new ServiceException(500,"菜单添加失败");
        }

        return Result.success(true);
    }

    @Override
    public Result<Boolean> editSysMenu(SysMenuVo sysMenuVo) {
        SysMenu sysMenu = BeanUtil.toBean(sysMenuVo, SysMenu.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysMenu.setUpdateBy(userInfo.getUsername());
        try {
            sysMenuMapper.editSysMenu(sysMenu);
        } catch (Exception e) {
            throw new ServiceException(500,"菜单更改失败");
        }
        return Result.success(true);
    }

    @Override
    public Result<SysMenuVo> getSysMenuInfo(Integer menuId) {
        SysMenu sysMenuInfo = sysMenuMapper.getSysMenuInfo(menuId);
        SysMenuVo sysMenuVo = BeanUtil.toBean(sysMenuInfo, SysMenuVo.class);
        return Result.success(sysMenuVo);
    }

    @Override
    public Result<Boolean> deleteSysMenuInfo(Integer[] menuIds) {
        try {
            for (Integer i : menuIds){
                sysMenuMapper.deleteSysMenuInfo(i);
            }
        } catch (Exception e) {
            throw new ServiceException(500,"菜单删除失败");
        }
        return Result.success(true);
    }

    @Override
    public Result<Boolean> changeSysMenuStatus(Integer menuId, Integer status) {
        try {
            sysMenuMapper.changeSysMenuStatus(menuId, status);
        } catch (Exception e) {
            throw new ServiceException(500,"菜单状态更改失败");
        }
        return Result.success(true);
    }
}
