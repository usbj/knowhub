package com.rookie.framework.security.mapper;

import com.rookie.framework.security.pojo.UserInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;


@Mapper
public interface UserInfoMapper {

    UserInfo selectUserByUsername(String username);

    ArrayList<Integer> selectRoleIdByUserId(Long userId);

    ArrayList<Integer> selectMenuIdByRoleId(ArrayList<Integer> roles);

    ArrayList<String> getPermKeyById(ArrayList<Integer> menus);

}
