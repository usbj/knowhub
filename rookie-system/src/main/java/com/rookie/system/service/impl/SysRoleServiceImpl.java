package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import com.rookie.common.pojo.entity.SysMenu;
import com.rookie.common.pojo.entity.SysRole;
import com.rookie.common.until.PageUntil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysRoleMapper;
import com.rookie.system.mapper.SysRoleMenuMapper;
import com.rookie.system.pojo.SysRoleMenu;
import com.rookie.system.pojo.quarry.RoleQuarry;
import com.rookie.system.pojo.vo.SysRoleVo;
import com.rookie.system.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Autowired
    SysRoleMapper sysRoleMapper;

    @Autowired
    SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public Result<PageInfo<SysRoleVo>> quarrySysRole(RoleQuarry roleQuarry) {
        //开启分页
        PageUntil.startPage();
        //获取数据
        List<SysRole> sysRoles = sysRoleMapper.quarrySysRole(roleQuarry);
        //封装分页
        List<SysRoleVo> roleVos = BeanUtil.copyToList(sysRoles, SysRoleVo.class);
        PageInfo<SysRoleVo> roleVoPageInfo = PageUntil.packagedPageInfo(roleVos);
        return Result.success(roleVoPageInfo);
    }

    @Override
    public Result<SysRoleVo> getSysRoleInfo(Long roleId) {
        return Result.success(sysRoleMapper.getSysRoleInfo(roleId));
    }

    @Override
    public Result<Boolean> editSysRoleInfo(SysRoleVo sysRoleVo) {
        //删除角色原先权限
        sysRoleMenuMapper.deleteSyeRoleMenu(sysRoleVo.getRoleId());

        //批量添加权限
        addMenuBulk(sysRoleVo);

        //转换为SysRole并添加上更新的人
        SysRole sysRole = BeanUtil.toBean(sysRoleVo, SysRole.class);
        UserInfo updateBy  = (UserInfo)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysRole.setUpdateBy(updateBy.getUsername());
        try {
            sysRoleMapper.editSysRoleInfo(sysRole);
        } catch (Exception e) {
            throw new ServiceException(500,"角色更改失败");
        }

        return Result.success(true);
    }

    @Override
    public Result<Boolean> addSysRoleInfo(SysRoleVo sysRoleVo) {
        //转换成entity实体，记录创建者，并存入数据库
        SysRole sysRole = BeanUtil.toBean(sysRoleVo, SysRole.class);
        UserInfo userInfo = (UserInfo)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysRole.setUpdateBy(userInfo.getUsername());
        sysRole.setCreateBy(userInfo.getUsername());
        try {
            sysRoleMapper.addSysRoleInfo(sysRole);
        } catch (Exception e) {
            throw new ServiceException(500,"角色基本信息添加失败");
        }

        //存入之后获取角色id，存入对应权限
        sysRoleVo.setRoleId(sysRole.getRoleId());
        addMenuIfMenuIsNotNull(sysRoleVo);

        return Result.success(true);
    }

    @Override
    public Result<Boolean> deleteSysRoleInfo(Long[] roleId) {

        try {
            for (Long id : roleId) {
                sysRoleMenuMapper.deleteSyeRoleMenu(id);
                sysRoleMapper.deleteSysRoleInfo(id);
            }
        } catch (Exception e) {
            throw new ServiceException(500,"角色删除失败");
        }

        return Result.success(true);
    }

    @Override
    public Result<Boolean> changeSysRoleStatus(Long roleId, Integer status) {
        return Result.success(sysRoleMapper.changeSysRoleStatus(roleId,status));
    }

    @Override
    public Result<Boolean> setTheDefaultRole(Long roleId) {
        try {
            sysRoleMapper.cancelTheDefaultRole();
            sysRoleMapper.setTheDefaultRole(roleId);
        } catch (Exception e) {
            throw new ServiceException(500,"默认角色更改失败");
        }
        return Result.success(true);
    }

    private void addMenuBulk(SysRoleVo sysRoleVo){
        if (sysRoleVo.getPermId().isEmpty()){
            sysRoleVo.setPermId(sysRoleVo.getRolePerm().stream().map(SysMenu::getMenuId).toList());
        }
        List<Long> permId = sysRoleVo.getPermId();
        ArrayList<SysRoleMenu> sysRoleMenus =new ArrayList<>();
        for (Long id : permId) {
            sysRoleMenus.add(new SysRoleMenu(sysRoleVo.getRoleId(),id));
        }
        try {
            sysRoleMenuMapper.insertSysRoleMenu(sysRoleMenus);
        } catch (Exception e) {
            throw new ServiceException(500,"角色的权限插入失败");
        }
    }

    private void addMenuIfMenuIsNotNull(SysRoleVo sysRoleVo){
        if ((sysRoleVo.getPermId()==null||sysRoleVo.getPermId().isEmpty())
                &&
                (sysRoleVo.getRolePerm()==null||sysRoleVo.getRolePerm().isEmpty())) {
            return;
        }
        addMenuBulk(sysRoleVo);
    }
}
