package com.rookie.framework.security.service;

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


        //获取用户权限
        ArrayList<String> permKeyById = null;
        try {
            ArrayList<Integer> roles = userInfoMapper.selectRoleIdByUserId(userInfo.getUserId());
            ArrayList<Integer> menus = userInfoMapper.selectMenuIdByRoleId(roles);
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
