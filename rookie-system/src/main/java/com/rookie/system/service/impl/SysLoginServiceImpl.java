package com.rookie.system.service.impl;


import cn.hutool.core.bean.BeanUtil;
import com.rookie.common.enums.ResultEnum;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import com.rookie.common.pojo.entity.SysRole;
import com.rookie.common.pojo.entity.SysUser;
import com.rookie.framework.security.mapper.UserInfoMapper;
import com.rookie.framework.security.service.TokenService;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysRoleMapper;
import com.rookie.system.mapper.SysUserMapper;
import com.rookie.system.mapper.SysUserRoleMapper;
import com.rookie.system.pojo.LoginBody;
import com.rookie.system.pojo.vo.SysMenuVo;
import com.rookie.system.pojo.vo.SysUserVo;
import com.rookie.system.service.SysLoginService;
import com.rookie.system.service.SysMenuService;
import com.rookie.system.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


@Service
public class SysLoginServiceImpl implements SysLoginService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    TokenService tokenService;

    @Autowired
    SysUserMapper sysUserMapper;

    @Autowired
    UserInfoMapper userInfoMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    SysRoleService sysRoleService;

    @Autowired
    SysMenuService sysMenuService;



    @Override
    public String loginVerification(LoginBody loginBody) {
        //根据用户输入的账号密码来获取验证以及身份信息
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken
                        (loginBody.getUsername(),loginBody.getPassword());
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);
        if (authenticate == null){
            throw new ServiceException(ResultEnum.COMMON_ERROR);
        }
        //获取用户信息
        UserInfo userInfo = (UserInfo) authenticate.getPrincipal();
        //生成JWT
        String token = tokenService.createJwt(userInfo);
        return token;
    }


    @Override
    public Boolean modifyPersonalDetails(SysUserVo sysUserVo) {
        SysUser sysUser = BeanUtil.toBean(sysUserVo, SysUser.class);
        try {
            sysUserMapper.editUserInfo(sysUser);
        } catch (Exception e) {
            throw new ServiceException(500,"用户信息更改失败");
        }
        return true;
    }

    @Override
    public SysUserVo getPersonalDetails(Long userId) {
        SysUser sysUser = sysUserMapper.getSysUserInfoById(userId);
        SysUserVo userVo = BeanUtil.toBean(sysUser, SysUserVo.class);
        return userVo;
    }

    @Override
    public Boolean resetSysUserPassword(Long userId, String password) {
        SysUser sysUser = new SysUser();
        sysUser.setPassword(password);
        sysUser.setUserId(userId);
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        try {
            sysUserMapper.resetSysUserPassword(sysUser);
        } catch (Exception e) {
            throw new ServiceException(500,"密码重置失败");
        }
        return true;
    }

    @Override
    public List<SysMenuVo> getUserMenuTreeByUserId(Long userId) {
        List<SysRole> sysRoles = sysRoleService.getSysRoleByUserId(userId);
        if (sysRoles == null || sysRoles.isEmpty()) {
            return new ArrayList<>();
        }
        // 超级管理员（roleKey == "admin"，状态启用）直通兜底：直接返回全部启用菜单（目录/菜单/按钮），
        // 不再依赖 role_menu 逐菜单授权，避免新增菜单后忘记给 admin 角色授权导致前端看不到目录/菜单/按钮。
        boolean isAdmin = sysRoles.stream()
                .anyMatch(role -> role.getStatus() != null && role.getStatus() != 0 && "admin".equals(role.getRoleKey()));
        List<SysMenuVo> sysMenuVos = isAdmin
                ? sysMenuService.getSysMenuAllEnabled()
                : sysMenuService.getSysMenuByRoleList(sysRoles);
        if (sysMenuVos == null || sysMenuVos.isEmpty()) {
            return new ArrayList<>();
        }
        return buildMenuTree(sysMenuVos);
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
