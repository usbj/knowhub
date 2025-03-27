package com.rookie.system.mapper;


import com.rookie.system.pojo.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysUserRoleMapper {

    boolean deleteUserRoleInfo(Long userId);

    boolean addUserRoleInfo(List<SysUserRole> sysUserRoles);



}
