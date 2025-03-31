package com.rookie.framework.security.service;

import com.rookie.common.pojo.entity.SysRole;
import com.rookie.framework.security.mapper.UserInfoMapper;
import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class UserDetailServiceImpl implements UserDetailsService {

    @Autowired
    UserInfoMapper userInfoMapper;



    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        //获取用户信息
        UserInfo userInfo = userInfoMapper.selectUserByUsername(username);
        if(userInfo==null) {
            return null;
        }

        /*
        * TODO 改写用户权限的记录方式，并设置管理员admin获取全部的权限
        * */

        //获取用户权限
        ArrayList<String> permKeyById = null;
        try {
            ArrayList<SysRole> roles = userInfoMapper.selectRoleByUserId(userInfo.getUserId());
            //如果角色状态为未启用则无法获取相应权限
            List<Long> roleIds = roles.stream().filter(sysRole -> sysRole.getStatus() != 0).map(SysRole::getRoleId).toList();
            ArrayList<Integer> menus = userInfoMapper.selectMenuIdByRoleId(roleIds);
            permKeyById = userInfoMapper.getPermKeyById(menus);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            //将用户权限的标识符转换为permission对象
            List<Permission> list = permKeyById.stream().filter(Objects::nonNull).map(Permission::new).toList();
            userInfo.setPermissions(list);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //返回
        return userInfo;
    }
}
