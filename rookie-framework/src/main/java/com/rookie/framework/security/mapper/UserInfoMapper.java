package com.rookie.framework.security.mapper;

import com.rookie.common.pojo.entity.SysRole;
import com.rookie.framework.security.pojo.UserInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;
import java.util.List;


@Mapper
public interface UserInfoMapper {

    UserInfo selectUserByUsername(String username);

    ArrayList<SysRole> selectRoleByUserId(Long userId);

    ArrayList<Integer> selectMenuIdByRoleId(List<Long> roles);

    ArrayList<String> getPermKeyById(ArrayList<Integer> menus);

}
